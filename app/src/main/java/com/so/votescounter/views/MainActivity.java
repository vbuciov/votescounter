package com.so.votescounter.views;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.so.votescounter.analyzers.ElectorsAnalizer;
import com.so.votescounter.api.IPictureAnalizer;
import org.opencv.android.OpenCVLoader;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import uk.co.senab.photoview.PhotoViewAttacher;


public class MainActivity extends AppCompatActivity
{
    private final static int START_CAMERA = 0;
    private final static int MULTIPLE_PERMISSIONS = 10;
    private final static String APP_PATH = Environment.getExternalStorageDirectory() + "/VotesCounter/";
    private Button btnExit, btnCamera, btnResults;
    private ImageView ivResultProcess;
    private SimpleDateFormat dateFormater;
    private File directoryInformation;
    private IPictureAnalizer formatToRecognize;
    private String fileName;
    private PhotoViewAttacher zoomImageAttacher;
    AlertDialog.Builder dlgResults;


    // Used to load the 'native-lib' library on application startup.
    static
    {
        System.loadLibrary("native-lib");
        if (!OpenCVLoader.initDebug()) {
            Log.w(MainActivity.class.getName(), "Unable to load OpenCV");
        } else {
            Log.i(MainActivity.class.getName(), "OpenCV loaded");
        }

        // For OCR
        //System.loadLibrary("gnustl_shared");
        //System.loadLibrary("nonfree");
    }

    //--------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //init Attributes
        directoryInformation = new File(APP_PATH);
        dateFormater = new SimpleDateFormat("yyMMddHHmmss");
        formatToRecognize = new ElectorsAnalizer(directoryInformation);
        dlgResults = new AlertDialog.Builder(this);

        //Init another Stuff
        initComponents();
        initEvents();
        checkForPermissions();
        checkApplicationFolder(directoryInformation);
    }

    //--------------------------------------------------------------------
    /**
     * Inicializa los componentes de la vista.
     * */
    private void initComponents()
    {
        btnExit =(Button) findViewById(R.id.btnExit);
        btnCamera = (Button) findViewById(R.id.btnCamera);
        btnResults = (Button) findViewById(R.id.btnResults);
        ivResultProcess = (ImageView) findViewById(R.id.ivResultProcess);
        zoomImageAttacher = new PhotoViewAttacher(ivResultProcess);
    }

    //--------------------------------------------------------------------
    /**
     * Inicializa los eventos que se utilizarán en la vista.
     * */
    private void initEvents()
    {
        View.OnClickListener mainActions = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainActions_onClick(v);
            }
        };

        btnCamera.setOnClickListener(mainActions);
        btnExit.setOnClickListener(mainActions);
        btnResults.setOnClickListener(mainActions);
    }


    //--------------------------------------------------------------------
    /**
     * Revisa si los permisos necesarios para operar la aplicación estan concedidos.
     * */
    private void checkForPermissions()
    {
        String[] PERMISSIONS = { Manifest.permission.CAMERA,
                                 Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                 Manifest.permission.INTERNET };

        if (!hasPermissions(this, PERMISSIONS))
        {
            ActivityCompat.requestPermissions(this, PERMISSIONS, MULTIPLE_PERMISSIONS);
        }
    }

    //--------------------------------------------------------------------
    /**
     * Verifica la lista de permisos en busqueda de que por lo menos falte uno.
     * */
    private boolean hasPermissions(Context context, String... permissions)
    {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null)
        {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    //--------------------------------------------------------------------
    /**
     * Revisa si el directorio recibido ya existe, y en caso de no ser así lo crea.
     * */
    private void checkApplicationFolder(File directoryInformation)
    {
        if (!directoryInformation.exists()) {
            if (!directoryInformation.mkdir())
            {
                Toast.makeText(this, "Necesitas permisos para utilizar esta aplicación", Toast.LENGTH_LONG );
            }
        }
    }

    //--------------------------------------------------------------------
    protected void mainActions_onClick(View v) {
        if (v == btnResults)
        {
            dlgResults.setMessage(String.format(" Total:%d [%s]",formatToRecognize.getMarkedCount(), formatToRecognize.getFormatedContent()));
            dlgResults.setTitle("Final Corte:");
            dlgResults.setPositiveButton("OK", null);
            dlgResults.setCancelable(true);
            dlgResults.show();

        }

        else if (v == btnExit)
            this.finish();

        else if (v == btnCamera)
            callNaitiveCamera();
    }

    //--------------------------------------------------------------------
    private void callNaitiveCamera() {
        try
        {
            File theNewImage = File.createTempFile("iv" + dateFormater.format(new Date()), ".jpg", directoryInformation);
            fileName = theNewImage.getAbsolutePath();
            Intent callCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            callCamera.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(theNewImage));
            startActivityForResult(callCamera, START_CAMERA);
        }

        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    //--------------------------------------------------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        //super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK)
        {
            switch (requestCode)
            {
                case START_CAMERA:
                   /* Bundle parameters = data.getExtras();
                    fileName = ((Uri)  parameters.get(MediaStore.EXTRA_OUTPUT)).getPath();*/

                    Bitmap MyImage = BitmapFactory.decodeFile(fileName);
                    if (MyImage != null)
                    {
                        ProcessImageAsyncTask secondPlane = new ProcessImageAsyncTask();
                        secondPlane.execute(MyImage);
                    }
                    else
                    Toast.makeText(this, "La imagen no pudo ser encontrada", Toast.LENGTH_LONG);
                    break;
            }
        }
    }


    //--------------------------------------------------------------------
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode)
        {
            case MULTIPLE_PERMISSIONS:
            {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permissions granted.
                } else {
                    this.finish();
                }
                return;
            }
        }
    }

    //--------------------------------------------------------------------
    public void dialogBox(String message, String bt1, String bt2, final boolean flagContinue) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(message);
        alertDialogBuilder.setPositiveButton(bt1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                if (flagContinue) {
                    callNaitiveCamera();
                }
            }
        });

        if (bt2 != "") {

            alertDialogBuilder.setNegativeButton(bt2, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface arg0, int arg1) {
                    //existApp();
                    // return false;
                }
            });
        }

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }


    //--------------------------------------------------------------------
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();


    //--------------------------------------------------------------------
    //--------------------------------------------------------------------
    private class ProcessImageAsyncTask extends AsyncTask<Object, Void, Bitmap> {

        ProgressDialog progressBar;

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            progressBar = ProgressDialog.show(MainActivity.this,
                    "Espere unos minutos por favor...",
                    "Procesando imagen", false, false);
        }

        @Override
        protected Bitmap doInBackground(Object... data)
        {
            Bitmap imageBitmap = (Bitmap)data[0];
            return formatToRecognize.analize(imageBitmap);
        }

        @Override
        protected void onPostExecute(Bitmap result)
        {
            if (progressBar != null && progressBar.isShowing())
                progressBar.dismiss();

            if (result != null)
            {
                ivResultProcess.setImageBitmap(result);
                zoomImageAttacher.update();
            }
            else
            {
                dialogBox("No fue posible el reconocimiento", "Reintentar", "Cancelar", true);
            }
        }
    }
}