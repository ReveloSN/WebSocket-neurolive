package com.neurolive.realtime.service;

import com.neurolive.realtime.model.RealtimeEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Publica eventos internos de forma desacoplada y no bloqueante.
@Service
public class RealtimeEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeEventPublisher.class);

    private final List<RealtimeEventObserver> observers;
    private final BlockingQueue<RealtimeEvent> eventQueue = new LinkedBlockingQueue<>();
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "realtime-event-dispatcher");
        thread.setDaemon(true);
        return thread;
    });

    // Recibe los observadores registrados en Spring.
    public RealtimeEventPublisher(List<RealtimeEventObserver> observers) {
        this.observers = observers;
    }

    // Inicia el ciclo de despacho en segundo plano.
    @PostConstruct
    public void startDispatcher() {
        dispatcher.submit(this::dispatchLoop);
    }

    // Agrega un evento a la cola interna.
    public void publish(RealtimeEvent event) {
        eventQueue.offer(event);
    }

    // Detiene el despachador al cerrar la aplicación.
    @PreDestroy
    public void stopDispatcher() {
        dispatcher.shutdownNow();
    }

    // Consume eventos y notifica a los observadores.
    private void dispatchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                RealtimeEvent event = eventQueue.take();
                dispatchEvent(event);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                LOGGER.error("Unexpected error while dispatching realtime event", exception);
            }
        }
    }

    // Notifica un evento a todos los observadores.
    private void dispatchEvent(RealtimeEvent event) {
        for (RealtimeEventObserver observer : observers) {
            try {
                observer.onEvent(event);
            } catch (Exception exception) {
                LOGGER.error("Observer {} failed for event {}", observer.getClass().getSimpleName(), event.type(), exception);
            }
        }
    }
}

