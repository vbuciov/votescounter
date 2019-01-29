package com.so.votescounter.api;

import android.graphics.Bitmap;

/**
 * Created by victor on 21/02/17.
 */

public interface IPictureAnalizer
{
    Bitmap analize (Bitmap value);
    String getFormatedContent();
    String getFormatedHeader();
    String getFormatedFooter();
    int getMarkedCount();
}
