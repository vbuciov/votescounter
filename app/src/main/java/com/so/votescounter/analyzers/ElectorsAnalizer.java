package com.so.votescounter.analyzers;

import android.graphics.Bitmap;
import android.util.Log;

import com.so.votescounter.api.IPictureAnalizer;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.lang.Math.nextAfter;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opencv.android.Utils.bitmapToMat;
import static org.opencv.android.Utils.matToBitmap;
import static org.opencv.core.Core.circle;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.core.Core.extractChannel;
import static org.opencv.core.Core.flip;
import static org.opencv.core.Core.insertChannel;
import static org.opencv.core.Core.line;
import static org.opencv.core.Core.rectangle;
import static org.opencv.core.Core.transpose;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.highgui.Highgui.imwrite;
import static org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C;
import static org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_MEAN_C;
import static org.opencv.imgproc.Imgproc.BORDER_CONSTANT;
import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE;
import static org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY;
import static org.opencv.imgproc.Imgproc.COLOR_BGR2Lab;
import static org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR;
import static org.opencv.imgproc.Imgproc.COLOR_Lab2BGR;
import static org.opencv.imgproc.Imgproc.Canny;
import static org.opencv.imgproc.Imgproc.GaussianBlur;
import static org.opencv.imgproc.Imgproc.INTER_CUBIC;
import static org.opencv.imgproc.Imgproc.INTER_LINEAR;
import static org.opencv.imgproc.Imgproc.MORPH_ELLIPSE;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.RETR_LIST;
import static org.opencv.imgproc.Imgproc.RETR_TREE;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY_INV;
import static org.opencv.imgproc.Imgproc.THRESH_OTSU;
import static org.opencv.imgproc.Imgproc.adaptiveThreshold;
import static org.opencv.imgproc.Imgproc.approxPolyDP;
import static org.opencv.imgproc.Imgproc.arcLength;
import static org.opencv.imgproc.Imgproc.bilateralFilter;
import static org.opencv.imgproc.Imgproc.blur;
import static org.opencv.imgproc.Imgproc.boundingRect;
import static org.opencv.imgproc.Imgproc.calcHist;
import static org.opencv.imgproc.Imgproc.contourArea;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.dilate;
import static org.opencv.imgproc.Imgproc.drawContours;
import static org.opencv.imgproc.Imgproc.equalizeHist;
import static org.opencv.imgproc.Imgproc.erode;
import static org.opencv.imgproc.Imgproc.findContours;
import static org.opencv.imgproc.Imgproc.getPerspectiveTransform;
import static org.opencv.imgproc.Imgproc.getStructuringElement;
import static org.opencv.imgproc.Imgproc.medianBlur;
import static org.opencv.imgproc.Imgproc.resize;
import static org.opencv.imgproc.Imgproc.threshold;
import static org.opencv.imgproc.Imgproc.warpPerspective;
import static org.opencv.ml.CvSVM.LINEAR;

/**
 * Created by Victor Manuel Bucio Vargas on 21/02/17.
 */

public class ElectorsAnalizer implements IPictureAnalizer
{
    private final static Scalar colorGrid = new Scalar(30, 255, 30);
    private final static Scalar colorMarked = new Scalar(255, 200, 00);
    private final static Scalar SAMPLE_WHITE = new Scalar(255,255,255);
    private final static Scalar SAMPLE_RED = new Scalar(0,0,255);
    private final static Scalar SAMPLE_BLUE = new Scalar(255,0,0);
    private final static Scalar SAMPLE_GREEN = new Scalar(200, 255, 0); // new Scalar(255, 0, 0, 255)
    private final static int COORD_X = 0;
    private final static int COORD_Y = 1;
    //private final static int COUNTOUR_PARENT = 3;
    private final static int TL_POSITION = 0;
    private final static int TR_POSITION = 1;
    private final static int BL_POSITION = 2;
    private final static int BR_POSITION = 3;
    private final static int thresholdMin = 90; // Threshold 80 to 105 is Ok
    private final static int thresholdMax = 255; // Always 255
    private final static int sourceWidth = 1024; // To scale to
    private final static int numCol = 25;
    private final static int numRow = 30;

    private File AppDirectory;
    private Point TL, TR, BL, BR;
    private int markedCount;
    private StringBuilder finalMessage;

    //--------------------------------------------------------------------
    public ElectorsAnalizer(File directory)
    {
        AppDirectory = directory;finalMessage = new StringBuilder();
    }

    //--------------------------------------------------------------------
    @Override
    public Bitmap analize(Bitmap value)
    {
        TR = null;
        TL = null;
        BL = null;
        BR = null;
        Bitmap result = null;
        Mat toResize = new Mat();
        markedCount = 0;

        if (finalMessage.length() > 0 )
        finalMessage.delete(0,finalMessage.length());

        try
        {
            bitmapToMat(value, toResize);
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            List<Rect> cornerSquares;
            //Size originalSize = toResize.size();
            Mat remarkedFill;
            Mat remarkedContours;
            Mat residue;
            MatOfPoint2f larggestSquare;

            if (value.getWidth() > value.getHeight())
                rotate90degrees(toResize);

            //0.- Resize origin image
            resize(toResize,
                    toResize,
                    //new Size(sourceWidth, originalSize.height * sourceWidth / originalSize.width),
                    new Size(0,0),
                    0.75, 0.75,
                    INTER_CUBIC);
            //writeImage("resize.jpg", toResize);

            //1.- We are going to correct the perspective.
            remarkedContours = processRemarkContours(toResize);

            //2.- Find the corners of the page.
            findContours(remarkedContours, contours, hierarchy, RETR_TREE, CHAIN_APPROX_SIMPLE);
            larggestSquare = processLargestSquareContour(contours, toResize.width() * toResize.height(), 0.33);

            if (larggestSquare != null  )
            {
                Mat borderPointsAsMatrix = processConvertToMatPoints(larggestSquare);

                //2.2.- Fit th e image only with the content Page.
                residue = remarkedContours;
                remarkedContours = processWarpStretch(remarkedContours, borderPointsAsMatrix);
                residue.release();
                residue = toResize;
                toResize = processWarpStretch(toResize, borderPointsAsMatrix);
                residue.release();
                writeImage("corrected.jpg", toResize);

                //2.3.- We have to find countours again.
                contours.clear();
                hierarchy.release();
                hierarchy = new Mat();
                findContours(remarkedContours, contours, hierarchy, RETR_LIST, CHAIN_APPROX_SIMPLE);
            }
            else
            {
                TL = new Point(0, 0);
                TR = new Point(toResize.width(), 0);
                BL = new Point(0, toResize.height());
                BR = new Point (toResize.width(), toResize.height());
            }

            //2.1.- We can draw the image with the points that we going to use
            /*if (TL != null && TR != null && BL != null && BR != null) {
            circle(toResize, TL, 55, SAMPLE_RED);
            circle(toResize, TR, 100, SAMPLE_RED);
            circle(toResize, BL, 55, SAMPLE_BLUE);
            circle(toResize, BR, 100, SAMPLE_WHITE);
            writeImage("lambda.jpg", toResize);
            }*/

            //cornerSquares = lookingForSquareMarkAtCorners(contours, hierarchy, toResize, 0.0015);
            cornerSquares = lookingForSquareMarkAtCorners(contours, hierarchy, toResize, 0.001295);
            writeImage("detected.jpg", toResize);

            if (cornerSquares.size() == 4)
            {
                //if(cornerSquares.get(1) != null)
                //{
                    //processGetOrientedMat();
                //}

                if (cornerSquares.get(0) != null && cornerSquares.get(3) != null) {
                    //3.- Cut the content image area.
                    Point temp = cornerSquares.get(0).tl();
                    TL = new Point(temp.x, temp.y + cornerSquares.get(0).height);
                    //TR = cornerSquares.get(1).br();
                    //BL = cornerSquares.get(2).tl();
                    temp = cornerSquares.get(3).br();
                    BR = new Point(temp.x, temp.y - cornerSquares.get(3).height);
                    Mat content = new Mat(toResize, new Rect(TL, BR));

                    //4.- Transform to gray the image for get better recognize process
                    remarkedFill = processRemarkFill(content);

                    Mat porcion;
                    int subAnsWidth = content.width();
                    int subAnsHeight = content.height();
                    double avrgWidth = subAnsWidth / (double)numCol;
                    double halfWidth = avrgWidth / 2;

                    double avrgHeight = subAnsHeight / (double)numRow;
                    double halfHeight = avrgHeight / 2;
                    double currentTopX, currentTopY, posYMin, posXMin;
                    int contador;

                    for (int j = 0; j < numRow; j++) {
                        currentTopY = j * avrgHeight;
                        posYMin = currentTopY + avrgHeight;
                        Point pl = new Point(0, posYMin);
                        Point pr = new Point(subAnsWidth, posYMin);
                        line(content, pl, pr, colorGrid, 2);

                        for (int i = 0; i < numCol; i++) {
                            currentTopX = i * avrgWidth;
                            if (j < 1) {
                                posXMin = i * avrgWidth + avrgWidth;
                                Point pt = new Point(posXMin, 0);
                                Point pb = new Point(posXMin, subAnsHeight);
                                line(content, pt, pb, colorGrid, 2);
                            }

                            porcion = new Mat(remarkedFill, new Rect(new Point(currentTopX, currentTopY),
                                    new Point(currentTopX + avrgWidth, currentTopY + avrgHeight)));

                            contador = countNonZero(porcion);

                            //The white count must be less to 70% of area
                            if (contador <= porcion.width() * porcion.height() * 0.75) {
                                circle(content, new Point(currentTopX + halfWidth, currentTopY + halfHeight),
                                        (int) halfWidth - 3, colorMarked, 3);
                                markedCount ++;
                                if(finalMessage.length()>0)
                                {
                                    finalMessage.append(",");
                                }
                                finalMessage.append((i+1) + (j*numCol));
                            }

                            porcion.release();
                        }
                    }

                    //writeImage("contenido.jpg",content);
                    content.release();
                    content = null;
                    remarkedFill.release();
                    remarkedFill = null;
                }

                /*info("Extrating Id");
                Mat subId = cropSub(subAnswer, new Point(avrgWidth, halfHeight), new Point(avrgWidth * 7, halfHeight),
                        new Point(avrgWidth, halfHeight * 5), new Point(avrgWidth * 7, halfHeight * 5));
                cvtColor(subId, subId, COLOR_BGR2GRAY);
                writeImage("id.png", subId);
                id = matToString(subId);
                subId.release();
                writeImage("sub_answer.png", drawAnswered(10));
                */
            }

            remarkedContours.release();
            remarkedContours = null;
            writeImage("success.jpg", toResize);
            result = createBitmapFrom(toResize);
        }

        catch (Exception e)
        {
            Log.e(ElectorsAnalizer.class.getName(), "Error parse image. Message: " + e.getMessage(), e);
        }

        finally
        {
            toResize.release();
        }

        return result;
    }

    //--------------------------------------------------------------------
    /**
     * Este proceso busca todos los cuadros en las esquinas de la imagen suministrada
     * cuya área sea aproximada en proporciones, al valor especificado.
     * */
    private List<Rect> lookingForSquareMarkAtCorners(List<MatOfPoint> contours, Mat hierarchy, Mat toResize, double aproxArea)
    {
        List<Rect> cornerSquares = new ArrayList<>();
        List<Rect> topLeftCorners = new ArrayList<>();
        List<Rect> topRightCorners = new ArrayList<>();
        List<Rect> bottomLeftCorners = new ArrayList<>();
        List<Rect> bottomRightCorners = new ArrayList<>();
        MatOfPoint current, points;
        MatOfPoint2f currentAsMatrix;
        MatOfPoint2f polygonAsMatrix;
        Rect currentRectangle;

        double currentArea, approxDistance,
                porcionY = toResize.height() / 5.0, porcionX = toResize.width() / 5.0,
                minArea = toResize.height() * toResize.width() * aproxArea,
                maxArea = minArea * 3;
        double halfY1 = porcionY, halfY2 = toResize.height() - porcionY,
                halfX1 = porcionX, halfX2 = toResize.width() - porcionX ;
        Point coordenates;
        //double[] contourInformation;

        //Search it must be in the remarked countours
        for (int i=0; i<contours.size(); i++)
        {
            current = contours.get(i);
            currentArea = contourArea(current);


            //The contour area is almost as we are looking for
            if (currentArea >= minArea)
            {
                polygonAsMatrix = new MatOfPoint2f();
                currentAsMatrix = new MatOfPoint2f(current.toArray());
                approxDistance = arcLength(currentAsMatrix, true) * 0.08;
                approxPolyDP(currentAsMatrix, polygonAsMatrix, approxDistance, true);

                //Polygon with four verticex is a Rectangle
                if (polygonAsMatrix.total() == 4)
                {
                    points = new MatOfPoint(polygonAsMatrix.toArray());
                    currentRectangle = boundingRect(points);
                    currentArea = currentRectangle.area();

                    /*rectangle(toResize, //Dest
                            currentRectangle.tl(), //Top-Left corner
                            currentRectangle.br(), //Bottom-right corner
                            SAMPLE_GREEN, 3);*/
                    //cornerSquares.add(currentRectangle);


                    if (currentArea <= maxArea)
                    {
                        coordenates = currentRectangle.tl();
                        //contourInformation = hierarchy.get(0, i);
                        if (coordenates.x <= halfX1)
                        {
                            if (coordenates.y <= halfY1)
                                topLeftCorners.add(currentRectangle);

                            else if (coordenates.y >= halfY2)
                                bottomLeftCorners.add(currentRectangle);
                        }

                        else if (coordenates.x >= halfX2) {
                            if (coordenates.y <= halfY1)
                                topRightCorners.add(currentRectangle);

                            else if (coordenates.y >= halfY2)
                                bottomRightCorners.add(currentRectangle);
                        }
                    }

                    else
                       points.release();
                }
                polygonAsMatrix.release();
                currentAsMatrix.release();
            }
        }

        if (topLeftCorners.size() > 0) {
            sortByDistance(topLeftCorners, TL);
            currentRectangle =topLeftCorners.get(0);
            cornerSquares.add(currentRectangle);

            rectangle(toResize, //Dest
                    currentRectangle.tl(), //Top-Left corner
                    currentRectangle.br(), //Bottom-right corner
                    SAMPLE_GREEN, 3);
        }
        else
            cornerSquares.add(null);

        if (topRightCorners.size() > 0) {
            sortByDistance(topRightCorners, TR);
            currentRectangle = topRightCorners.get(0);
            cornerSquares.add(currentRectangle);

            rectangle(toResize, //Dest
                    currentRectangle.tl(), //Top-Left corner
                    currentRectangle.br(), //Bottom-right corner
                    SAMPLE_GREEN, 3);
        }
        else
            cornerSquares.add(null);

        if (bottomLeftCorners.size() > 0) {
            sortByDistance(bottomLeftCorners, BL);
            currentRectangle =bottomLeftCorners.get(0);
            cornerSquares.add(currentRectangle);

            rectangle(toResize, //Dest
                    currentRectangle.tl(), //Top-Left corner
                    currentRectangle.br(), //Bottom-right corner
                    SAMPLE_GREEN, 3);
        }
        else
            cornerSquares.add(null);

        if (bottomRightCorners.size() > 0) {
            sortByDistance(bottomRightCorners, BR);
            currentRectangle =bottomRightCorners.get(0);
            cornerSquares.add(currentRectangle);

            rectangle(toResize, //Dest
                    currentRectangle.tl(), //Top-Left corner
                    currentRectangle.br(), //Bottom-right corner
                    SAMPLE_GREEN, 3);
        }

        else
            cornerSquares.add(null);

        topLeftCorners.clear();
        topRightCorners.clear();
        bottomLeftCorners.clear();
        bottomRightCorners.clear();

        return cornerSquares;
    }

    //--------------------------------------------------------------------
    public void sortByDistance (List<Rect> toSorter, Point aproximation)
    {
        Rect currentRectangle, anotherRectangle;
        Point p1, p2;
        //1.- We have to order the Points.
        for (int i = 0; i < toSorter.size(); i++ )
        {
            for (int j = 0; j < toSorter.size() - 1; j++)
            {
                currentRectangle = toSorter.get(j);
                p1 = currentRectangle.tl();

                anotherRectangle = toSorter.get(j + 1);
                p2 = anotherRectangle.tl();

                if (pow(aproximation.x - p1.x  + 1, 2) + pow(aproximation.y - p1.y + 1, 2) >
                        pow (aproximation.x - p2.x + 1, 2) + pow (aproximation.y - p2.y + 1, 2)) {
                    toSorter.set(j, anotherRectangle);
                    toSorter.set(j + 1, currentRectangle );
                }
            }
        }
    }

    //--------------------------------------------------------------------
    /**
     * Convierte el contorno representado por puntos flotantes
     * En una matriz de puntos de 4x4
     * */
    private Mat processConvertToMatPoints(MatOfPoint2f larggestSquare)
    {
        List<Point> pointsFound = new ArrayList<>();
        double[] p2Coordinates, p1Coordinates, swapCoordinates;


        //1.- We have to order the Points.
        for (int i = TL_POSITION; i <= BR_POSITION; i++ )
        {
            for (int j = TL_POSITION; j < BR_POSITION; j++)
            {
                p1Coordinates = larggestSquare.get(j, 0);
                p2Coordinates = larggestSquare.get(j + 1, 0);

                if (pow(p1Coordinates[COORD_X] + 1, 2 ) + pow(p1Coordinates[COORD_Y] + 1, 2)  >
                        pow(p2Coordinates[COORD_X]  + 1, 2) + pow(p2Coordinates[COORD_Y] + 1, 2)) {
                    swapCoordinates = p1Coordinates;
                    p1Coordinates = p2Coordinates;
                    p2Coordinates = swapCoordinates;
                    larggestSquare.put(j, 0, p1Coordinates);
                    larggestSquare.put(j + 1, 0, p2Coordinates);
                }
            }
        }

        //2.- Asinate each identify border.
        p1Coordinates = larggestSquare.get(TL_POSITION, 0);
        TL = new Point(p1Coordinates[COORD_X], p1Coordinates[COORD_Y]);

        p1Coordinates = larggestSquare.get(TR_POSITION, 0);
        TR = new Point(p1Coordinates[COORD_X], p1Coordinates[COORD_Y]);

        p1Coordinates = larggestSquare.get(BL_POSITION, 0);
        BL = new Point(p1Coordinates[COORD_X], p1Coordinates[COORD_Y]);

        p1Coordinates = larggestSquare.get(BR_POSITION, 0);
        BR = new Point(p1Coordinates[COORD_X], p1Coordinates[COORD_Y]);

        pointsFound.add(TL);
        pointsFound.add(TR);
        pointsFound.add(BL);
        pointsFound.add(BR);

        return  Converters.vector_Point2f_to_Mat(pointsFound);
    }

    //--------------------------------------------------------------------
    public void rotate90degrees(Mat inputImage) {
        transpose(inputImage, inputImage);
        flip(inputImage, inputImage, 1); // 1 es equal to 90°
    }


    //--------------------------------------------------------------------
    /**
     * Procesa una lista de contornos para localizar el contorno más grande
     * que se aproxime al aŕea especificada en relación a toda el área.
     * */
    private MatOfPoint2f processLargestSquareContour(List<MatOfPoint> contours, double wholeArea, double aproxArea)
    {
        double maxArea = -1;
        MatOfPoint current;
        MatOfPoint2f larggest = null;
        MatOfPoint2f currentAsMatrix;
        MatOfPoint2f polygonAsMatrix;
        double currentArea, approxDistance;

         //2.2.- Find the more largest contour
        for (int i = 0; i < contours.size(); i++)
        {
            current = contours.get(i);
            currentArea = contourArea(current);

            // compare this contour to the previous largest contour found
            if (currentArea > maxArea && currentArea >= wholeArea * aproxArea)
            {
                currentAsMatrix = new MatOfPoint2f(current.toArray());
                polygonAsMatrix = new MatOfPoint2f();
                approxDistance = arcLength(currentAsMatrix, true) * 0.08;
                approxPolyDP(currentAsMatrix, polygonAsMatrix, approxDistance, true);

                //Polygon with 4 corners (check if this contour is a square)
                if (polygonAsMatrix.total() == 4)
                {
                    if (larggest != null)
                        larggest.release();
                    maxArea = currentArea;
                    larggest = polygonAsMatrix;
                }

                else
                {
                    polygonAsMatrix.release();
                }

                currentAsMatrix.release();
            }
        }

        return larggest;
    }

    //--------------------------------------------------------------------
    /**
     * Provoca que una porcion de la imagen indicada por los puntos de origen,
     * sean extendidos al tamaño entero de la imagen.
     * */
    public Mat processWarpStretch(Mat inputMat, Mat startPoints)
    {
        Size originSize = inputMat.size();
        List<Point> pointsSet = new ArrayList<>();
        Mat endPoints;
        Point ocvPOut1, ocvPOut2, ocvPOut3, ocvPOut4;
        Mat result = new Mat((int)originSize.height, (int)originSize.width, inputMat.type());

        ocvPOut1 = new Point(0, 0);
        ocvPOut2 = new Point(originSize.width, 0);
        ocvPOut3 = new Point(0, originSize.height);
        ocvPOut4 = new Point(originSize.width, originSize.height);

        pointsSet.add(ocvPOut1);
        pointsSet.add(ocvPOut2);
        pointsSet.add(ocvPOut3);
        pointsSet.add(ocvPOut4);

        endPoints = Converters.vector_Point2f_to_Mat(pointsSet);
        Mat perspectiveTransform = getPerspectiveTransform(startPoints, endPoints);
        warpPerspective(inputMat, result, perspectiveTransform, originSize);

        return result;
    }

    //--------------------------------------------------------------------
    /**
     * Remarca todos los bordes que existan en la imagen.
     * */
    private Mat processRemarkContours(Mat origin)
    {
        Mat result =  increaseBrightness(origin, 2.0F, 10); //origin.clone();
        //Mat element1 = getStructuringElement(MORPH_RECT, new Size(3, 3), new Point(1, 1));
        //Mat element2 = getStructuringElement(MORPH_RECT, new Size(3, 3), new Point(1, 1));
        Mat element1 = getStructuringElement(MORPH_ELLIPSE, new Size(9, 9), new Point(3, 3));
        Mat element2 = getStructuringElement(MORPH_ELLIPSE, new Size(9, 9), new Point(3, 3));
        Mat element3 = getStructuringElement(MORPH_RECT, new Size(3, 3), new Point(1, 1));

        cvtColor(result, result, COLOR_BGR2GRAY);
        dilate(result, result, element1);
        erode(result, result, element2);

        //GaussianBlur(result, result, new Size(3, 3), 0);
        medianBlur(result, result, 3);
        Canny(result, result, 50, 50);

        //threshold(result, result, thresholdMin, thresholdMax, THRESH_BINARY | THRESH_OTSU);
        adaptiveThreshold(result, result, thresholdMax, ADAPTIVE_THRESH_MEAN_C, THRESH_BINARY, 5, 0);
        //equalizeHist(result, result);
        dilate(result, result, element3);

        writeImage("canny.jpg", result);

        return result;
    }

    //--------------------------------------------------------------------
    /**
     * Remarca unicamente los rellenos en negro.
     * */
    private Mat processRemarkFill (Mat origin)
    {
        Mat result =  increaseBrightness(origin, 1.5F, 15); //new Mat(origin.height(), origin.width(), CV_8UC1 );
        Mat element1 = getStructuringElement(MORPH_ELLIPSE, new Size(9, 9), new Point(3, 3));
        Mat element2 = getStructuringElement(MORPH_ELLIPSE, new Size(9, 9), new Point(3, 3));

        cvtColor(origin, result, COLOR_BGR2GRAY);
        dilate(result, result, element1);
        erode(result, result, element2);

        GaussianBlur(result, result, new Size(3, 3), 0);
        adaptiveThreshold(result, result, thresholdMax, ADAPTIVE_THRESH_MEAN_C, THRESH_BINARY, 41, 11);
        //threshold(result, result, thresholdMin, thresholdMax, THRESH_BINARY);
        medianBlur(result, result, 5);
        writeImage("gray.jpg", result); // Black-White image*/

        return result;
    }

    //--------------------------------------------------------------------
    private Mat increaseBrightness(Mat origin, float alpha, int beta) {
        Mat src = new Mat(origin.rows(), origin.cols(),  CV_8UC1);
        origin.convertTo(src, CV_8UC1);

        src.convertTo(src, -1, alpha, beta);
        //writeImage("brig.jpg", result);

        return src;
    }

    //--------------------------------------------------------------------
    @Override
    public String getFormatedContent()
    {
        return finalMessage.toString();
    }

    //--------------------------------------------------------------------
    @Override
    public String getFormatedHeader() {
        return null;
    }

    //--------------------------------------------------------------------
    @Override
    public String getFormatedFooter() {
        return null;
    }

    //--------------------------------------------------------------------
    @Override
    public int getMarkedCount() {
        return markedCount;
    }

    //--------------------------------------------------------------------
    /***
     * Escribe una imagen en el directorio de la aplicación.
     */
    private void writeImage(String name, Mat origin)
    {
        String newImageFile = AppDirectory.getAbsolutePath() + "/" + name;
        Log.d(ElectorsAnalizer.class.getName(), "Writing " + newImageFile  + "...");
        imwrite(newImageFile, origin);
    }

    //--------------------------------------------------------------------
    /**
     * Crea un mapa de bits a partir de una matriz.
     * */
    public Bitmap createBitmapFrom(Mat origin)
    {
        Bitmap bitmap = Bitmap.createBitmap(origin.width(), origin.height(), Bitmap.Config.ARGB_8888);
        matToBitmap(origin, bitmap);
        return bitmap;
    }

    /**
     * Check if marked
     *
     * @param src
     * @param row
     * @param col
     * @return
     */
    /*public boolean isMarked(Mat src, int row, int col) {
        double channels = src.channels();
        // In case src is Gray
        if (channels == 1) {
            // Check around the point
            for (int i = 0; i <= numCol; i++) {
                if (src.get(row + i, col)[0] == 0 || src.get(row, col + i)[0] == 0 || src.get(row + i, col + i)[0] == 0
                        || src.get(row - i, col)[0] == 0 || src.get(row, col - i)[0] == 0
                        || src.get(row - i, col - i)[0] == 0) {

                    return true;
                }
            }
        }
        return false;
    }*/

    // Math
    /*public double getLineYIntesept(Point p, double slope) {
        return p.y - slope * p.x;
    }

    public Point findIntersectionPoint(Point line1Start, Point line1End, Point line2Start, Point line2End)
    {
        double slope1 = (line1End.x - line1Start.x) == 0 ? (line1End.y - line1Start.y)
                : (line1End.y - line1Start.y) / (line1End.x - line1Start.x);

        double slope2 = (line2End.x - line2Start.x) == 0 ? (line2End.y - line2Start.y)
                : (line2End.y - line2Start.y) / (line2End.x - line2Start.x);

        double yinter1 = getLineYIntesept(line1Start, slope1);
        double yinter2 = getLineYIntesept(line2Start, slope2);

        if (slope1 == slope2 && yinter1 != yinter2)
            return null;

        double x = (yinter2 - yinter1) / (slope1 - slope2);
        double y = slope1 * x + yinter1;

        return new Point(x, y);
    }*/

    //--------------------------------------------------------------------
    /*private Mat processGetOrientedMat(Mat origin, List<Rect> squares) {
        Rect aux = new Rect();
        Mat rotated = new Mat();
        int position = -1;
        for (int i = 0; i < squares.size(); i++) {
            aux = squares.get(i);
            if (aux != null && aux.width != aux.height) {
                break;
            }
        }

        if (aux != null) {
            if (origin.height() > origin.width()) {
                if (aux.tl().x > .5 * origin.width() && aux.tl().y < .5 * origin.height()) {
                    Log.d(">>>UBICACION:", "Esquina Superior Derecha");
                } else if (aux.tl().x < .5 * origin.width() && aux.tl().y > .5 * origin.height()) {
                    flip(origin, rotated, -1);
                    writeImage("rotada.jpg", rotated);
                }
            } else if (origin.width() > origin.height()) {
                if (aux.tl().x > .5 * origin.width()) {
                    flip(origin, rotated, 0);
                    writeImage("rotada.jpg", rotated);
                } else if (aux.tl().x < .5 * origin.width()) {
                    flip(origin, rotated, 1);
                    writeImage("rotada.jpg", rotated);
                }
            }
        }
        return origin;
    }*/
}
