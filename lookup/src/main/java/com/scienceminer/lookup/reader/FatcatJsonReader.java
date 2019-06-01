package com.scienceminer.lookup.reader;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class FatcatJsonReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FatcatJsonReader.class);
    private LookupConfiguration configuration;

    public FatcatJsonReader(LookupConfiguration configuration) {
        this.configuration = configuration;
    }

    public void load(String input, Consumer<String> closure) {
        try (Stream<String> stream = Files.lines(Paths.get(input))) {

            stream.forEach(line -> closure.accept(line));
        } catch (IOException e) {
            LOGGER.error("Some serious error when processing the input Fatcat file.", e);
        }

    }

    public void load(InputStream input, Consumer<JsonNode> closure) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {

            //br returns as stream and convert it into a List
            br.lines().forEach(line -> {
                final JsonNode fatcatData = fromJson(line);
                if (fatcatData == null) {
                    return;
                }

                /*
                //Ignoring empty DOI
                if (fatcatData.get("DOI") == null || isBlank(fatcatData.get("DOI").asText())) {
                    return;
                }
                */

                //Ignoring document of type component
                if (fatcatData.get("release_type") != null && 
                    (StringUtils.equals(fatcatData.get("release_type").asText(), "stub")
                     || StringUtils.equals(fatcatData.get("release_type").asText(), "abstract"))) {
                    return;
                }

                ObjectNode object = (ObjectNode) fatcatData;
                if (configuration != null && configuration.getIgnoreFatcatFields() != null) {
                    for(String field : configuration.getIgnoreFatcatFields()) {
                        object.remove(field);
                    }
                }
                //object.remove("_id");

                closure.accept(fatcatData);
            });

        } catch (IOException e) {
            LOGGER.error("Some serious error when processing the input Fatcat file.", e);
        }
    }

    public JsonNode fromJson(String inputLine) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
            mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            return mapper.readTree(inputLine);
        } catch (JsonGenerationException | JsonMappingException e) {
            LOGGER.error("The input line cannot be processed\n " + inputLine + "\n ", e);
        } catch (IOException e) {
            LOGGER.error("Some serious error when deserialize the JSON object: \n" + inputLine, e);
        }
        return null;
    }
}
