package com.neurolive.realtime.service;

import com.neurolive.realtime.model.RealtimeEvent;

// Define un observador para eventos internos del servicio.
public interface RealtimeEventObserver {

    // Procesa un evento publicado por el servicio.
    void onEvent(RealtimeEvent event);
}

