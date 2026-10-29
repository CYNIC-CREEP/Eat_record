"""Compare FP16 MNN against ONNX, including automatic whole-meal candidate selection."""
import argparse
import json
import pathlib
import sys
import time
ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/deps/vision/python"))
import MNN
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageOps

def mnn(path, inputs):
    net = MNN.Interpreter(str(path))
    net.setSessionMode(1)
    net.setSessionHint(3, 0)
    session = net.createSession({"backend": "CPU", "numThread": 2, "precision": "low"})
    for name, values in inputs.items():
        tensor = net.getSessionInput(session, name)
        net.resizeTensor(tensor, values.shape)
    net.resizeSession(session)
    for name, values in inputs.items():
        net.getSessionInput(session, name).copyFrom(MNN.Tensor(values.shape, MNN.Halide_Type_Float,
            np.ascontiguousarray(values, dtype=np.float32), MNN.Tensor_DimensionType_Caffe))
    started = time.perf_counter()
    assert net.runSession(session) == 0
    result = {}
    for name, tensor in net.getSessionOutputAll(session).items():
        shape = tensor.getShape()
        host = MNN.Tensor(shape, MNN.Halide_Type_Float, np.zeros(shape, dtype=np.float32), MNN.Tensor_DimensionType_Caffe)
        tensor.copyToHostTensor(host)
        result[name] = np.array(host.getData(), dtype=np.float32).reshape(shape)
    return result, time.perf_counter()-started

parser = argparse.ArgumentParser()
parser.add_argument("--image", required=True)
parser.add_argument("--alpha", required=True)
parser.add_argument("--output", default=str(ROOT / "build/beta8-edgetam-parity"))
args = parser.parse_args()
out = pathlib.Path(args.output); out.mkdir(parents=True, exist_ok=True)
models = ROOT / "build/deps/edgetam/export"
options = ort.SessionOptions(); options.intra_op_num_threads = 2
encoder = ort.InferenceSession(str(models / "encoder.onnx"), sess_options=options)
decoder = ort.InferenceSession(str(models / "decoder.onnx"), sess_options=options)
photo = ImageOps.exif_transpose(Image.open(args.image)).convert("RGB")
photo.thumbnail((1024, 1024)); w,h=photo.size
pixels = np.asarray(photo.resize((1024, 1024), Image.Resampling.BILINEAR), dtype=np.float32)/255.0
data = np.ascontiguousarray(((pixels-np.array([.485,.456,.406]))/np.array([.229,.224,.225])).transpose(2,0,1)[None], dtype=np.float32)
inputs={"image":data}
ort_features=dict(zip([v.name for v in encoder.get_outputs()], encoder.run(None,inputs)))
features, seconds=mnn(models / "encoder.mnn",inputs)
print(json.dumps({"encoder_seconds":seconds,"features":{k:[list(v.shape),float(np.mean(np.abs(v-ort_features[k])))] for k,v in features.items()}}), flush=True)
ai=Image.open(args.alpha); ai=ai.getchannel("A") if ai.mode=="RGBA" else ai.convert("L")
alpha=np.asarray(ai.resize((512,512),Image.Resampling.BILINEAR))/255.
fg=alpha>.5; yy,xx=np.where(fg)
box=np.array([xx.min(),yy.min(),xx.max()+1,yy.max()+1], dtype=np.float32)*2
reports=[]; selected=None; best=-1
for margin in [0,.04,.08]:
    expanded=np.clip(box+np.array([-1,-1,1,1],dtype=np.float32)*1024*margin,0,1023).reshape(1,2,2)
    inputs={**features,"box":expanded}
    reference=decoder.run(None,{**ort_features,"box":expanded})
    values, seconds=mnn(models / "decoder.mnn",inputs)
    masks=values["masks"][0]; scores=values["scores"][0]
    for i,(mask,score) in enumerate(zip(masks,scores)):
        ref=reference[0][0,i]>0; binary=mask>0
        iou=float((ref&binary).sum()/max(1,(ref|binary).sum()))
        large=np.asarray(Image.fromarray(binary.astype(np.uint8)*255).resize((512,512),Image.Resampling.NEAREST))>127
        coverage=float((fg&large).sum()/fg.sum()); area=float(large.sum()/fg.sum())
        report={"margin":margin,"index":i,"score":float(score),"coverage":coverage,"area":area,"parity_iou":iou,"seconds":seconds}
        reports.append(report)
        print(json.dumps(report),flush=True)
        if score>=.7 and coverage>=.94:
            assert iou>.95, report
        # Accept only a confidently supported enclosing subject, never arbitrary larger background.
        if score>=.7 and coverage>=.94 and 1.12<=area<=2.3 and large.mean()<.85 and score>best:
            best=float(score); selected=large
if selected is None:
    selected=fg
else:
    selected|=fg
sticker=photo.convert("RGBA")
sticker.putalpha(Image.fromarray(selected.astype(np.uint8)*255).resize(photo.size,Image.Resampling.BILINEAR))
sticker.save(out/"selected.png")
preview=Image.new("RGB",(w*2,h+30),"#eee9e2"); preview.paste(photo,(0,30)); preview.paste(sticker,(w,30),sticker)
ImageDraw.Draw(preview).text((10,8),f"Original / automatic MNN result (score {best:.3f})",fill="black")
preview.save(out/"comparison.jpg",quality=92)
(out/"report.json").write_text(json.dumps(reports,indent=2),encoding="utf-8")
