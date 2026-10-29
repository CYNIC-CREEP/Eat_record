"""Export only EdgeTAM's single-image modules; no video memory or old SAM workflow."""
import pathlib
import sys
ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / "build/deps/edgetam/python"), str(ROOT / "build/deps/edgetam/source"), str(ROOT / "build/deps/vision/python")]
import torch
import timm.models
original_create = timm.models.create_model
def offline_create(*args, **kwargs):
    kwargs["pretrained"] = False
    return original_create(*args, **kwargs)
timm.models.create_model = offline_create
from sam2.build_sam import build_sam2

class Encoder(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model
    def forward(self, image):
        features = self.model.forward_image(image)["backbone_fpn"]
        return features[2] + self.model.no_mem_embed.reshape(1, 256, 1, 1), features[0], features[1]

class Decoder(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.prompt = model.sam_prompt_encoder
        self.decoder = model.sam_mask_decoder
        self.register_buffer("pe", self.prompt.get_dense_pe())
        self.register_buffer("labels", torch.tensor([[2, 3]], dtype=torch.int32))
    def forward(self, embedding, high0, high1, box):
        # Equivalent fixed box labels [2,3] plus the predictor's padding token.
        # Avoid PyTorch boolean-index assignments, which export malformed Where
        # broadcasts on older mobile converters.
        coords = ((box + .5) / 1024.0 * 2.0 - 1.0)
        encoded = coords @ self.prompt.pe_layer.positional_encoding_gaussian_matrix
        encoded = encoded * (2.0 * 3.141592653589793)
        corners = torch.cat([torch.sin(encoded), torch.cos(encoded)], dim=-1)
        corners = corners + torch.cat([self.prompt.point_embeddings[2].weight,
                                        self.prompt.point_embeddings[3].weight], dim=0).unsqueeze(0)
        sparse = torch.cat([corners, self.prompt.not_a_point_embed.weight.reshape(1, 1, 256)], dim=1)
        dense = self.prompt.no_mask_embed.weight.reshape(1,256,1,1).expand(1,256,64,64)
        result = self.decoder(image_embeddings=embedding, image_pe=self.pe,
            sparse_prompt_embeddings=sparse, dense_prompt_embeddings=dense,
            multimask_output=True, repeat_image=False, high_res_features=[high0, high1])
        return result[0], result[1]

if __name__ == "__main__":
    torch.set_num_threads(2)
    destination = ROOT / "build/deps/edgetam/export"
    destination.mkdir(parents=True, exist_ok=True)
    model = build_sam2("configs/edgetam.yaml", str(ROOT / "build/deps/edgetam/edgetam.pt"), device="cpu", apply_postprocessing=False)
    image = torch.zeros(1, 3, 1024, 1024)
    encoder, decoder = Encoder(model).eval(), Decoder(model).eval()
    with torch.no_grad():
        features = encoder(image)
        torch.onnx.export(encoder, (image,), str(destination / "encoder.onnx"), opset_version=17,
            input_names=["image"], output_names=["embedding", "high0", "high1"])
        torch.onnx.export(decoder, (*features, torch.tensor([[[200., 200.], [800., 800.]]])),
            str(destination / "decoder.onnx"), opset_version=17,
            input_names=["embedding", "high0", "high1", "box"], output_names=["masks", "scores"])
    print("Exported single-image encoder and box-prompt mask decoder", flush=True)
