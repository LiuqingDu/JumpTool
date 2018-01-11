package com.duliuqing.jumptool;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;



public class JumpTool
{

    private static final String IMAGE_NAME    = "current.png";

    private static final String STORE_DIR    = "/Users/tong/Downloads/jump_screencapture";

    private static final int imageLengthLength  = 5;

    private static final long[] imageLength    = new long[imageLengthLength];

    private final RGBInfo  rgbInfo     = new RGBInfo();

    private final String[]  ADB_SCREEN_CAPTURE_CMDS =
            { "adb shell screencap -p /sdcard/" + IMAGE_NAME,
                    "adb pull /sdcard/current.png " + STORE_DIR };

    private final int   gameScoreBottomY  = 300;

    private final double  pressTimeCoefficient = 1.35;

    private final int   swipeX     = 550;

    private final int   swipeY     = 1580;

    private final int   halfBaseBoardHeight  = 20;

    private final int   halmaBodyWidth   = 74;

    private final int   boardX1     = 813;

    private final int   boardY1     = 1122;

    private final int   boardX2     = 310;

    private final int   boardY2     = 813;

    private int[] getHalmaAndBoardXYValue(File currentImage) throws IOException
    {
        BufferedImage bufferedImage = ImageIO.read(currentImage);
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        int halmaXSum = 0;
        int halmaXCount = 0;
        int halmaYMax = 0;
        int boardX = 0;
        int boardY = 0;
        for (int y = gameScoreBottomY; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                processRGBInfo(bufferedImage, x, y);
                int rValue = this.rgbInfo.getRValue();
                int gValue = this.rgbInfo.getGValue();
                int bValue = this.rgbInfo.getBValue();
                if (rValue > 50 && rValue < 60 && gValue > 53 && gValue < 63 && bValue > 95 && bValue < 110)
                {
                    halmaXSum += x;
                    halmaXCount++;
                    halmaYMax = y > halmaYMax ? y : halmaYMax;
                }
            }
        }

        if (halmaXSum != 0 && halmaXCount != 0)
        {
            int halmaX = halmaXSum / halmaXCount;
            int halmaY = halmaYMax - halfBaseBoardHeight;
            for (int y = gameScoreBottomY; y < height; y++)
            {
                processRGBInfo(bufferedImage, 0, y);
                int lastPixelR = this.rgbInfo.getRValue();
                int lastPixelG = this.rgbInfo.getGValue();
                int lastPixelB = this.rgbInfo.getBValue();
                if (boardX > 0)
                {
                    break;
                }
                int boardXSum = 0;
                int boardXCount = 0;
                for (int x = 0; x < width; x++)
                {
                    processRGBInfo(bufferedImage, x, y);
                    int pixelR = this.rgbInfo.getRValue();
                    int pixelG = this.rgbInfo.getGValue();
                    int pixelB = this.rgbInfo.getBValue();
                    if (Math.abs(x - halmaX) < halmaBodyWidth)
                    {
                        continue;
                    }
                    if ((Math.abs(pixelR - lastPixelR) + Math.abs(pixelG - lastPixelG) + Math.abs(pixelB - lastPixelB)) > 10)
                    {
                        boardXSum += x;
                        boardXCount++;
                    }
                }

                if (boardXSum > 0)
                {
                    boardX = boardXSum / boardXCount;
                }
            }
            boardY = (int) (halmaY - Math.abs(boardX - halmaX) * Math.abs(boardY1 - boardY2)
                    / Math.abs(boardX1 - boardX2));
            if (boardX > 0 && boardY > 0)
            {
                int[] result = new int[4];
                result[0] = halmaX;
                result[1] = halmaY;
                result[2] = boardX;
                result[3] = boardY;
                return result;
            }
        }

        return null;
    }

    private void executeCommand(String command)
    {
        Process process = null;
        try
        {
            process = Runtime.getRuntime().exec(command);
            System.out.println("exec command start: " + command);
            process.waitFor();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line = bufferedReader.readLine();
            if (line != null)
            {
                System.out.println(line);
            }
            System.out.println("exec command end: " + command);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (process != null)
            {
                process.destroy();
            }
        }
    }

    private void executeADBCaptureCommands()
    {
        for (String command : ADB_SCREEN_CAPTURE_CMDS)
        {
            executeCommand(command);
        }
    }

    private void doJump(double distance)
    {
        System.out.println("distance: " + distance);
        int pressTime = (int) Math.max(distance * pressTimeCoefficient, 200);
        System.out.println("pressTime: " + pressTime);
        String command = String.format("adb shell input swipe %s %s %s %s %s", swipeX, swipeY, swipeX, swipeY,
                pressTime);
        System.out.println(command);
        executeCommand(command);
    }

    private void replayGame()
    {
        String command = String.format("adb shell input tap %s %s", swipeX, swipeY);
        executeCommand(command);
    }

    private double computeJumpDistance(int halmaX, int halmaY, int boardX, int boardY)
    {
        return Math.sqrt(Math.pow(Math.abs(boardX - halmaX), 2) + Math.pow(Math.abs(boardY - halmaY), 2));
    }

    public static void main(String[] args)
    {
        try
        {
            File storeDir = new File(STORE_DIR);
            if (!storeDir.exists()) {
                boolean flag = storeDir.mkdir();
                if (!flag) {
                    System.err.println("创建图片存储目录失败");
                    return;
                }
            }

            JumpTool jumpjumpHelper = new JumpTool();
            int executeCount = 0;
            for (;;)
            {
                jumpjumpHelper.executeADBCaptureCommands();
                File currentImage = new File(STORE_DIR, IMAGE_NAME);
                if (!currentImage.exists())
                {
                    System.out.println("图片不存在");
                    continue;
                }

                long length = currentImage.length();
                imageLength[executeCount % imageLengthLength] = length;
                jumpjumpHelper.checkDoReplay();
                executeCount++;
                System.out.println("当前第" + executeCount + "次执行!");
                int[] result = jumpjumpHelper.getHalmaAndBoardXYValue(currentImage);
                if (result == null)
                {
                    System.out.println("The result of method getHalmaAndBoardXYValue is null!");
                    continue;
                }
                int halmaX = result[0];
                int halmaY = result[1];
                int boardX = result[2];
                int boardY = result[3];
                System.out.println("halmaX: " + halmaX + ", halmaY: " + halmaY + ", boardX: " + boardX + ", boardY: "
                        + boardY);
                double jumpDistance = jumpjumpHelper.computeJumpDistance(halmaX, halmaY, boardX, boardY);
                jumpjumpHelper.doJump(jumpDistance);
                TimeUnit.MILLISECONDS.sleep(2500);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void checkDoReplay()
    {
        if (imageLength[0] > 0 && imageLength[0] == imageLength[1] && imageLength[1] == imageLength[2]
                && imageLength[2] == imageLength[3] && imageLength[3] == imageLength[4])
        {
            Arrays.fill(imageLength, 0);
            replayGame();
        }
    }

    private void processRGBInfo(BufferedImage bufferedImage, int x, int y)
    {
        this.rgbInfo.reset();
        int pixel = bufferedImage.getRGB(x, y);
        this.rgbInfo.setRValue((pixel & 0xff0000) >> 16);
        this.rgbInfo.setGValue((pixel & 0xff00) >> 8);
        this.rgbInfo.setBValue((pixel & 0xff));
    }

    class RGBInfo
    {
        private int RValue;

        private int GValue;

        private int BValue;

        public int getRValue()
        {
            return RValue;
        }

        public void setRValue(int rValue)
        {
            RValue = rValue;
        }

        public int getGValue()
        {
            return GValue;
        }

        public void setGValue(int gValue)
        {
            GValue = gValue;
        }

        public int getBValue()
        {
            return BValue;
        }

        public void setBValue(int bValue)
        {
            BValue = bValue;
        }

        public void reset()
        {
            this.RValue = 0;
            this.GValue = 0;
            this.BValue = 0;
        }
    }
}