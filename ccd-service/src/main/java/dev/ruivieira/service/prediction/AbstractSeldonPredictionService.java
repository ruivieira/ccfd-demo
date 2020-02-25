/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.ruivieira.service.prediction;

import dev.ruivieira.service.messages.PredictionRequest;
import dev.ruivieira.service.messages.PredictionResponse;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.kie.api.task.model.Task;
import org.kie.internal.task.api.prediction.PredictionOutcome;
import org.kie.internal.task.api.prediction.PredictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class AbstractSeldonPredictionService implements PredictionService {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSeldonPredictionService.class);
    protected final ResteasyClient client;
    protected final ResteasyWebTarget predict;
    private double confidenceThreshold = 1.0;

    private static final String SELDON_URL_KEY = "org.jbpm.task.prediction.service.seldon.url";
    private static final String CONFIDENCE_THRESHOLD_KEY = "org.jbpm.task.prediction.service.seldon.confidence_threshold";
    private static final String SELDON_TIMEOUT_KEY = "org.jbpm.task.prediction.service.seldon.timeout";
    private static final String SELDON_CONNECTION_POOL_SIZE_KEY = "org.jbpm.task.prediction.service.seldon.connection_pool_size";

    public AbstractSeldonPredictionService() {
        // Seldon connection configuration
//        Configurations configs = new Configurations();
//        CompositeConfiguration compositeConfiguration = new CompositeConfiguration();
//
//        Configuration javaProperties = new PropertiesConfiguration();
//        Configuration systemProperties = new SystemConfiguration();
//
//        compositeConfiguration.addConfiguration(javaProperties);
//        compositeConfiguration.addConfiguration(systemProperties);
//        try {
//            Configuration config = configs.properties(new File("seldon.properties"));
//            compositeConfiguration.addConfiguration(config);
//        } catch (ConfigurationException e) {
//            logger.debug("Could not find the file 'seldon.properties'. Trying other configuration sources.");
//        }
//
//        final String SELDON_URL = compositeConfiguration.getString(SELDON_URL_KEY);
        final String SELDON_URL = System.getenv("SELDON_URL");

        if (SELDON_URL == null) {
            final String errorMessage = "No Seldon endpoint URL specified";
            logger.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        logger.debug("Using Seldon endpoint " + SELDON_URL);

        ResteasyClientBuilder clientBuilder = new ResteasyClientBuilder();

//        final String seldonTimeoutStr = compositeConfiguration.getString(SELDON_TIMEOUT_KEY);
        final String seldonTimeoutStr = System.getenv("SELDON_TIMEOUT");

        if (seldonTimeoutStr!=null) {
            try {
                final long seldonTimeout = Long.parseLong(seldonTimeoutStr);
                clientBuilder = clientBuilder.connectionCheckoutTimeout(seldonTimeout, TimeUnit.MILLISECONDS);
                logger.info("Seldon connection timeout set to " + seldonTimeout + " milliseconds");
            } catch (NumberFormatException e) {
                logger.error("Invalid Seldon connection timeout");
            }
        }

//        final String seldonConnectioPoolSizeStr = compositeConfiguration.getString(SELDON_CONNECTION_POOL_SIZE_KEY);
        final String seldonConnectioPoolSizeStr = System.getenv("SELDON_CONNECTION_POOL");

        if (seldonConnectioPoolSizeStr!=null) {
            try {
                final int seldonConnectioPoolSize = Integer.parseInt(seldonConnectioPoolSizeStr);
                clientBuilder = clientBuilder.connectionPoolSize(seldonConnectioPoolSize);
                logger.info("Seldon connection pool size set to " + seldonConnectioPoolSize);
            } catch (NumberFormatException e) {
                logger.error("Invalid Seldon connection pool size");
            }
        }

        client = clientBuilder.build();

        predict = client.target(SELDON_URL).path("predict");

        // set confidence threshold from configuration
//        final String CONFIDENCE_THRESHOLD = compositeConfiguration.getString(CONFIDENCE_THRESHOLD_KEY);
        final String CONFIDENCE_THRESHOLD = System.getenv("CONFIDENCE_THRESHOLD");

        if (CONFIDENCE_THRESHOLD != null) {
            try {
                this.confidenceThreshold = Double.parseDouble(CONFIDENCE_THRESHOLD);
                logger.info("Setting confidence threshold to " + this.confidenceThreshold);
            } catch (NumberFormatException e) {
                logger.error("Invalid confidence threshold in org.jbpm.task.prediction.service.seldon.confidence_threshold");
            }
        } else {
            logger.info("Using default confidence threshold of 1.0");
        }
    }

    /**
     * Returns a model prediction given the input data.
     */
    @Override
    public PredictionOutcome predict(Task task, Map<String, Object> map) {
        logger.info("Building features");
        final List<List<Double>> features = buildPredictFeatures(task, map);
        try {
            System.out.println("Trying to send a request for " + map);
            final PredictionRequest request = new PredictionRequest();
            request.addFeatures(features.get(0));
            final String json = PredictionRequest.toJSON(request);
            final String stringResponse = predict.request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.entity(json, MediaType.APPLICATION_JSON_TYPE), String.class);
            final PredictionResponse response = PredictionResponse.fromString(stringResponse);
            final Map<String, Object> parsedResponse = parsePredictFeatures(response);
            return new PredictionOutcome((Double) parsedResponse.get("confidence"), this.confidenceThreshold, parsedResponse);
        } catch (Exception e) {
            System.out.println("Failed!");
            logger.error(e.getMessage());
        }
        System.out.println("Returning empty prediction");
        return new PredictionOutcome();
    }

    /**
     * Model training is not supported in this service with Seldon. This is a NO-OP.
     */
    @Override
    public void train(Task task, Map<String, Object> map, Map<String, Object> map1) {
        // Training not supported
    }

    /**
     * Construct a list of numerical features which can be passed to Seldon with the provided
     * task and input data.
     * It returns a two-dimensional numerical list where each list element corresponds
     * to a single prediction request's features.
     * This is domain specific and must be implemented in the concrete service.
     *
     * @param task Human task data
     * @param map A map containing the input attribute names as keys and the attribute values as values.
     * @return A two-dimensional list of numerical features.
     */
    public abstract List<List<Double>> buildPredictFeatures(Task task, Map<String, Object> map);

    /**
     * Builds the {@link PredictionOutcome} data from the deserialized Seldon response.
     * This is domain specific and must be implemented in the concrete service.
     *
     * @param response Deserialized Seldon's response
     * @return A {@link Map} as the {@link PredictionOutcome} data
     */
    public abstract Map<String, Object> parsePredictFeatures(PredictionResponse response);

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }
}
