package com.muhammaddaffa.nextgens.api.events.generators;

import com.muhammaddaffa.nextgens.objects.Generator;

public class GeneratorLoadEvent extends GeneratorEvent {

    public GeneratorLoadEvent(Generator generator) {
        super(generator);
    }

}
