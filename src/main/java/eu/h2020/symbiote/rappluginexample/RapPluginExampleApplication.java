package eu.h2020.symbiote.rappluginexample;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.model.cim.Location;
import eu.h2020.symbiote.model.cim.Observation;
import eu.h2020.symbiote.model.cim.ObservationValue;
import eu.h2020.symbiote.model.cim.Property;
import eu.h2020.symbiote.model.cim.UnitOfMeasurement;
import eu.h2020.symbiote.model.cim.WGS84Location;
import eu.h2020.symbiote.rapplugin.messaging.RabbitManager;
import eu.h2020.symbiote.rapplugin.messaging.rap.ActuatorAccessListener;

import eu.h2020.symbiote.rapplugin.messaging.rap.RapPlugin;
import eu.h2020.symbiote.rapplugin.messaging.rap.RapPluginException;
import eu.h2020.symbiote.rapplugin.messaging.rap.ServiceAccessListener;
import eu.h2020.symbiote.rapplugin.messaging.rap.SimpleResourceAccessListener;
import eu.h2020.symbiote.rapplugin.properties.Properties;
import eu.h2020.symbiote.rapplugin.value.Value;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackageClasses = {RapPlugin.class, RabbitManager.class, Properties.class})
public class RapPluginExampleApplication implements CommandLineRunner {

    public static final Logger LOG = LoggerFactory.getLogger(RapPluginExampleApplication.class);
    private static final String TYPE_SENSOR = "sensor";
    private static final String TYPE_OBSERVATION = "observation";

    @Autowired
    RapPlugin rapPlugin;

    public static void main(String[] args) {
        SpringApplication.run(RapPluginExampleApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        // use either of the two ResourceAccessListener implementations
        // SimpleResourceAccessListener for simple (backwards-compatible) style
        // ResourceAccessListener for more functionality including filters and PIM support
        rapPlugin.registerReadingResourceListener(new SimpleResourceAccessListener() {
            @Override
            public List<Observation> readResourceHistory(String resourceId) {
                if ("rp_isen1".equals(resourceId)) // This is the place to put reading history data of sensor.
                {
                    return new ArrayList<>(Arrays.asList(createObservation(resourceId),
                            createObservation(resourceId), createObservation(resourceId)));
                } else {
                    throw new RapPluginException(404, "Sensor not found.");
                }
            }

            @Override
            public Observation readResource(String resourceId) {
                if ("rp_isen1".equals(resourceId)) {
                    // This is place to put reading data from sensor 
                    return createObservation(resourceId);
                }
                throw new RapPluginException(404, "Sensor not found.");
            }
        });

//        rapPlugin.registerReadingResourceListener(new ResourceAccessListener() {
//            @Override
//            public String getResource(List<ResourceInfo> resourceInfo) {
//                if (resourceInfo.size() == 2 
//                        && TYPE_SENSOR.equals(resourceInfo.get(0).getType()) 
//                        && TYPE_OBSERVATION.equals(resourceInfo.get(1).getType())
//                        && "rp_isen1".equals(resourceInfo.get(1).getInternalId())) {
//                    try {
//                        return new ObjectMapper().writeValueAsString(createObservation(resourceInfo.get(1).getInternalId()));
//                    } catch (JsonProcessingException ex) {
//                        throw new RapPluginException(404, "Could not serialize data. Reason: " + ex.getMessage());
//                    }
//                }
//                throw new RapPluginException(404, "Resource not found.");
//            }
//
//            @Override
//            public String getResourceHistory(List<ResourceInfo> resourceInfo, int top, Query filterQuery) {
//                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//            }
//        });
        rapPlugin.registerActuatingResourceListener(new ActuatorAccessListener() {
            @Override
            public void actuateResource(String internalId, Map<String, Map<String, Value>> capabilities) {
                System.out.println("Called actuation for resource " + internalId);
                capabilities.entrySet().forEach(capability -> {
                    System.out.println("Capability: " + capability.getKey());
                    capability.getValue().entrySet().forEach(parameter
                            -> System.out.println(" " + parameter.getKey() + " = " + parameter.getValue()));
                });
                if ("rp_iaid1".equals(internalId)) {
                    // This is place to put actuation code for resource with id
                    System.out.println("iaid1 is actuated");
                    return;
                } else {
                    throw new RapPluginException(404, "Actuating entity not found.");
                }
            }
        });

        rapPlugin.registerInvokingServiceListener(new ServiceAccessListener() {
            @Override
            public String invokeService(String internalId, Map<String, Value> parameters) {
                parameters.entrySet().forEach(parameter
                        -> System.out.println(" " + parameter.getKey() + " = " + parameter.getValue()));
                if ("rp_isrid1".equals(internalId)) {
                    return "ok";
                } else {
                    throw new RapPluginException(404, "Service not found.");
                }
            }
        });
    }

    public Observation createObservation(String sensorId) {
        Location loc = new WGS84Location(48.2088475, 16.3734492, 158, "Stephansdome", Arrays.asList("City of Wien"));

        TimeZone zoneUTC = TimeZone.getTimeZone("UTC");
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        dateFormat.setTimeZone(zoneUTC);
        Date date = new Date();
        String timestamp = dateFormat.format(date);

        long ms = date.getTime() - 1000;
        date.setTime(ms);
        String samplet = dateFormat.format(date);

        ObservationValue obsval
                = new ObservationValue(
                        "7",
                        new Property("Temperature", "TempIRI", Arrays.asList("Air temperature")),
                        new UnitOfMeasurement("C", "degree Celsius", "C_IRI", null));
        ArrayList<ObservationValue> obsList = new ArrayList<>();
        obsList.add(obsval);

        Observation obs = new Observation(sensorId, loc, timestamp, samplet, obsList);

        try {
            LOG.debug("Observation: \n{}", new ObjectMapper().writeValueAsString(obs));
        } catch (JsonProcessingException e) {
            LOG.error("Can not convert observation to JSON", e);
        }

        return obs;
    }
}
