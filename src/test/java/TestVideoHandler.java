import com.hujian.mp4.Mp4Handler;

import java.io.IOException;

/**
 * Created by hujian on 2017/4/7.
 */
public class TestVideoHandler {

    public static void main(String[] args) throws IOException {

        String srcFile =  "C:\\Users\\hujian\\Downloads\\Mp4\\test.mp4";

        Mp4Handler mp4Handler = new Mp4Handler(srcFile,4);

        mp4Handler.split();

    }

}
