package com.voiceassistant.partner;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import com.voiceassistant.partner.IPartnerCallback;

interface IPartnerBroker {
    Bundle getCapabilities();
    Bundle beginSession(in Bundle hello);
    void submitAudio(String sessionId, String requestId, in ParcelFileDescriptor audio, in Bundle options, IPartnerCallback callback);
    void cancel(String sessionId, String requestId);
}
