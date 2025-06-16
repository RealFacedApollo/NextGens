package com.muhammaddaffa.nextgens.api.events.generators;

import com.muhammaddaffa.nextgens.objects.ActiveGenerator;
import com.muhammaddaffa.nextgens.objects.Generator;

public class GeneratorCorruptedEvent extends GeneratorEvent {

    private final ActiveGenerator activeGenerator;

    public GeneratorCorruptedEvent(Generator generator, ActiveGenerator activeGenerator) {
        super(generator);
        this.activeGenerator = activeGenerator;
    }

    public ActiveGenerator getActiveGenerator() {
        return activeGenerator;
    }

}
