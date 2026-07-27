package com.voiceassistant.partner;

import android.os.Bundle;

interface IPartnerCallback {
    void onProgress(in Bundle update);
    void onCompleted(in Bundle result);
}
