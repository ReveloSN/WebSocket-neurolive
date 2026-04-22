package com.neurolive.realtime.websocket;

import com.neurolive.realtime.exception.InvalidDeviceMessageException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// Resuelve el procesador adecuado segun el tipo entrante.
@Component
public class MessageProcessorFactory {

    private final Map<String, MessageProcessor> processorsByType;

    // Construye el mapa de procesadores registrados.
    public MessageProcessorFactory(List<MessageProcessor> processors) {
        this.processorsByType = processors.stream()
                .collect(Collectors.toUnmodifiableMap(MessageProcessor::supportedType, Function.identity()));
    }

    // Retorna el procesador correspondiente al tipo indicado.
    public MessageProcessor getProcessor(String type) {
        MessageProcessor processor = processorsByType.get(type);
        if (processor == null) {
            throw new InvalidDeviceMessageException("Unsupported message type: " + type);
        }
        return processor;
    }
}

