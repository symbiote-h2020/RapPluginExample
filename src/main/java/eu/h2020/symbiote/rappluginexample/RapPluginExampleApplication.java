package eu.h2020.symbiote.rappluginexample;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.WaitForPort;
import eu.h2020.symbiote.cloud.model.rap.ResourceInfo;
import eu.h2020.symbiote.cloud.model.rap.query.Query;
import eu.h2020.symbiote.model.cim.Location;
import eu.h2020.symbiote.model.cim.Observation;
import eu.h2020.symbiote.model.cim.ObservationValue;
import eu.h2020.symbiote.model.cim.Property;
import eu.h2020.symbiote.model.cim.UnitOfMeasurement;
import eu.h2020.symbiote.model.cim.WGS84Location;
import eu.h2020.symbiote.rapplugin.messaging.rap.ActuatorAccessListener;
import eu.h2020.symbiote.rapplugin.messaging.rap.RapPlugin;
import eu.h2020.symbiote.rapplugin.messaging.rap.RapPluginException;
import eu.h2020.symbiote.rapplugin.messaging.rap.ResourceAccessListener;
import eu.h2020.symbiote.rapplugin.messaging.rap.ServiceAccessListener;
import eu.h2020.symbiote.rapplugin.messaging.rap.SubscriptionListener;
import eu.h2020.symbiote.rapplugin.util.Utils;
import eu.h2020.symbiote.rapplugin.value.PrimitiveValue;
import eu.h2020.symbiote.rapplugin.value.Value;

@SpringBootApplication
public class RapPluginExampleApplication implements CommandLineRunner {
  public static final Logger LOG = LoggerFactory.getLogger(RapPluginExampleApplication.class);

  @Autowired
  RapPlugin rapPlugin;

  private ObjectMapper mapper = new ObjectMapper();

  public static void main(String[] args) {
    WaitForPort.waitForServices(WaitForPort.findProperty("SPRING_BOOT_WAIT_FOR_SERVICES"));
    SpringApplication.run(RapPluginExampleApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    registerListeners();
  }

  private void registerListeners() {
    rapPlugin.registerReadingResourceListener(new ResourceAccessListener() {

      @Override
      public String getResourceHistory(List<ResourceInfo> resourceInfo, int top, Query filterQuery) {
        LOG.debug("reading resource history with info {}", resourceInfo);

        String resourceId = Utils.getInternalResourceId(resourceInfo);

        if ("rp_isen1".equals(resourceId) || "isen1".equals(resourceId)) {
          // This is the place to put reading history data of sensor.
          List<Observation> observations = new LinkedList<>();
          for (int i = 0; i < top; i++) {
            observations.add(createObservation(resourceId));
          }

          try {
            return mapper.writeValueAsString(observations);
          } catch (JsonProcessingException e) {
            throw new RapPluginException(500, "Can not convert observations to JSON", e);
          }
        } else {
          throw new RapPluginException(404, "Sensor not found.");
        }
      }

      @Override
      public String getResource(List<ResourceInfo> resourceInfo) {
        LOG.debug("reading resource with info {}", resourceInfo);

        String resourceId = Utils.getInternalResourceId(resourceInfo);

        if ("rp_isen1".equals(resourceId) || "isen1".equals(resourceId)) {
          // This is place to put reading data from sensor
          try {
            return mapper.writeValueAsString(createObservation(resourceId));
          } catch (JsonProcessingException e) {
            throw new RapPluginException(500, "Can not convert observation to JSON", e);
          }
        }

        throw new RapPluginException(404, "Sensor not found.");
      }
    });

    rapPlugin.registerActuatingResourceListener(new ActuatorAccessListener() {
      @Override
      public void actuateResource(String internalId, Map<String, Map<String, Value>> capabilities) {
        System.out.println("Called actuation for resource " + internalId);
        // print capabilities
        for (Entry<String, Map<String, Value>> capabilityEntry : capabilities.entrySet()) {
          System.out.println("Capability: " + capabilityEntry.getKey());

          for (Entry<String, Value> parameterEntry : capabilityEntry.getValue().entrySet()) {
            System.out.print(" " + parameterEntry.getKey() + " = ");
            PrimitiveValue primitiveValue = parameterEntry.getValue().asPrimitive();
            if (primitiveValue.isString()) {
              System.out.println(primitiveValue.asString());
            } else if (primitiveValue.isInt()) {
              System.out.println(primitiveValue.asInt());
            } else {
              System.out.println(primitiveValue.toString());
            }
          }
        }

        if ("rp_iaid1".equals(internalId) || "iaid1".equals(internalId)) {
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
        System.out.println("In invoking service of resource " + internalId);

        // print parameters
        for (Entry<String, Value> parameterEntry : parameters.entrySet()) {
          System.out.println(" Parameter - name: " + parameterEntry.getKey() + " value: " +
              parameterEntry.getValue().asPrimitive().asString());
        }

        try {
          if ("rp_isrid1".equals(internalId)) {
            return mapper.writeValueAsString("ok");
          } else if ("isrid1".equals(internalId)) {
            return mapper.writeValueAsString("some json");
          } else {
            throw new RapPluginException(404, "Service not found.");
          }
        } catch (JsonProcessingException e) {
          throw new RapPluginException(500, "Can not convert service response to JSON", e);
        }
      }
    });

    rapPlugin.registerNotificationResourceListener(new SubscriptionListener() {

      private Set<String> subscriptionSet = Collections.synchronizedSet(new HashSet<>());
      private volatile Thread subscriptionThread;

      @Override
      public void unsubscribeResource(String resourceId) {
        LOG.info("Unsubscribe to {}", resourceId);
        synchronized (subscriptionSet) {
          subscriptionSet.remove(resourceId);
        }

      }

      private void sendPush() {
        LOG.info("Sending notifications!!!!");
        synchronized (subscriptionSet) {
          for (String id : subscriptionSet) {
            Observation observation = createObservation(id);
            rapPlugin.sendNotification(observation);
            LOG.info("Notification for resource {}: {}", id, observation);
          }
        }
      }

      @Override
      public void subscribeResource(String resourceId) {
        LOG.info("Subscribing to {}", resourceId);
        synchronized (subscriptionSet) {
          subscriptionSet.add(resourceId);
          if (subscriptionThread == null) {
            subscriptionThread = new Thread(() -> {
              while (!subscriptionSet.isEmpty()) {
                sendPush();
                try {
                  Thread.sleep(5000);
                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
              }
              subscriptionThread = null;
            });
            subscriptionThread.start();
          }
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

    ObservationValue obsval = new ObservationValue(
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
