package org.vedenemo.cli;

import java.io.IOException;
import java.util.List;

public interface ModelClient {

    List<ModelSummary> listModels() throws IOException, InterruptedException;

    ModelSummary addModel(String azName, String visName, String version) throws IOException, InterruptedException;
}
