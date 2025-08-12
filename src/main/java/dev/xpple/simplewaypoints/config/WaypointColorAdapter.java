package dev.xpple.simplewaypoints.config;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class WaypointColorAdapter extends TypeAdapter<WaypointColor> {
    @Override
    public void write(JsonWriter writer, WaypointColor waypointColor) throws IOException {
        writer.value(waypointColor.color());
    }

    @Override
    public WaypointColor read(JsonReader reader) throws IOException {
        return new WaypointColor(reader.nextInt());
    }
}
