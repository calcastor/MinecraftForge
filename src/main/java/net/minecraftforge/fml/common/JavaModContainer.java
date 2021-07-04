package net.minecraftforge.fml.common;

import com.google.common.eventbus.EventBus;
import scala.actors.threadpool.Arrays;

import java.util.Collections;

public class JavaModContainer extends DummyModContainer {
    public JavaModContainer() {
        super(new ModMetadata());
        ModMetadata meta = getMetadata();

        meta.name = "Java";
        meta.modId = "java";
        meta.version = System.getProperty("java.version", "Unknown");
        meta.description = "Java is a high-level, class-based, object-oriented programming language that is " +
            "designed to have as few implementation dependencies as possible.";
        meta.authorList = Collections.singletonList("Oracle");
        meta.url = "https://www.java.com";
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        return true;
    }
}
