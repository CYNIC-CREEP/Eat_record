package com.eatrecord.app;

import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;

/** File-selection regression; this never opens a window, decodes a photo, or changes app data. */
public final class FoodPreviewSelectionTest {
    private static int checks;
    private static void expect(String wanted,String actual){
        checks++;
        if(!wanted.equals(actual))throw new AssertionError("Unexpected private preview source");
    }
    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("eat-record-preview-");
        Path original=folder.resolve("source.jpg"),cutout=folder.resolve("cutout.png"),directory=folder.resolve("directory");
        try{
            Files.write(original,new byte[]{1});Files.write(cutout,new byte[]{2});Files.createDirectory(directory);
            JSONObject food=new JSONObject().put("sourcePhoto","source.jpg").put("cutoutFile","cutout.png");
            expect("source.jpg",FoodUi.preferredPreviewFile(folder.toFile(),food));
            Files.delete(original);
            expect("cutout.png",FoodUi.preferredPreviewFile(folder.toFile(),food));
            food.put("sourcePhoto","directory");
            expect("cutout.png",FoodUi.preferredPreviewFile(folder.toFile(),food));
            for(String unsafe:new String[]{"../source.jpg","sub/source.jpg","sub\\source.jpg","/source.jpg",""}){
                food.put("sourcePhoto",unsafe);
                expect("cutout.png",FoodUi.preferredPreviewFile(folder.toFile(),food));
            }
            food.put("sourcePhoto",original.toAbsolutePath().toString());
            expect("cutout.png",FoodUi.preferredPreviewFile(folder.toFile(),food));
            food.put("sourcePhoto","missing.jpg").put("cutoutFile","missing.png");
            expect("",FoodUi.preferredPreviewFile(folder.toFile(),food));
            food.put("sourcePhoto","../source.jpg").put("cutoutFile","../cutout.png");
            expect("",FoodUi.preferredPreviewFile(folder.toFile(),food));
            expect("",FoodUi.preferredPreviewFile(folder.toFile(),new JSONObject()));
            expect("",FoodUi.preferredPreviewFile(folder.toFile(),null));
            expect("",FoodUi.preferredPreviewFile(null,food));
            System.out.println("PASS: "+checks+" private original/cutout selection checks (no image decoding or device claim)");
        }finally{
            Files.deleteIfExists(original);Files.deleteIfExists(cutout);Files.deleteIfExists(directory);Files.deleteIfExists(folder);
        }
    }
}
