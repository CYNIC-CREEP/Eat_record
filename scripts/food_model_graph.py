"""Deterministic spatial-only adaptation of the pinned rembg IS-Net export.

The source export baked its 1024px skip-connection Resize targets into constants.
Changing only the graph input shape is invalid. For the tested 512px mobile
variant, scale those exact spatial targets and discard unused auxiliary outputs.
No weights or learned operators are changed. See assets/licenses/ISNet-NOTICE.txt.
"""
def mobile_isnet(source, destination):
    import onnx
    from onnx import numpy_helper
    graph = onnx.load(str(source))
    assert graph.graph.input[0].name == "input_image"
    assert graph.graph.input[0].type.tensor_type.shape.dim[2].dim_value == 1024
    producers = {output: node for node in graph.graph.node for output in node.output}
    adapted = 0
    for resize in graph.graph.node:
        if resize.op_type != "Resize":
            continue
        assert len(resize.input) == 4
        concat = producers[resize.input[3]]
        assert concat.op_type == "Concat" and len(concat.input) == 2
        constant = producers[concat.input[1]]
        assert constant.op_type == "Constant" and constant.attribute[0].name == "value"
        values = numpy_helper.to_array(constant.attribute[0].t).copy()
        assert values.shape == (2,) and (values >= 2).all() and (values % 2 == 0).all()
        constant.attribute[0].t.CopyFrom(numpy_helper.from_array(values // 2))
        adapted += 1
    assert adapted == 39, adapted
    shape = graph.graph.input[0].type.tensor_type.shape.dim
    shape[2].dim_value = shape[3].dim_value = 512
    primary = next(output for output in graph.graph.output if output.name == "output_image")
    primary.type.tensor_type.shape.dim[2].dim_value = 512
    primary.type.tensor_type.shape.dim[3].dim_value = 512
    del graph.graph.output[:]
    graph.graph.output.append(primary)
    del graph.graph.value_info[:]
    onnx.checker.check_model(graph)
    onnx.save(graph, str(destination))
