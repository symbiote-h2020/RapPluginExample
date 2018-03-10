package eu.h2020.symbiote.rappluginexample;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.data.InputParameter;
import eu.h2020.symbiote.cloud.model.data.Result;
import eu.h2020.symbiote.model.cim.Location;
import eu.h2020.symbiote.model.cim.Observation;
import eu.h2020.symbiote.model.cim.ObservationValue;
import eu.h2020.symbiote.model.cim.Property;
import eu.h2020.symbiote.model.cim.UnitOfMeasurement;
import eu.h2020.symbiote.model.cim.WGS84Location;
import eu.h2020.symbiote.rapplugin.domain.Capability;
import eu.h2020.symbiote.rapplugin.domain.Parameter;
import eu.h2020.symbiote.rapplugin.messaging.rap.ActuatingResourceListener;
import eu.h2020.symbiote.rapplugin.messaging.rap.InvokingServiceListener;
import eu.h2020.symbiote.rapplugin.messaging.rap.RapPlugin;
import eu.h2020.symbiote.rapplugin.messaging.rap.RapPluginException;
import eu.h2020.symbiote.rapplugin.messaging.rap.ReadingResourceListener;

@SpringBootApplication
public class RapPluginExampleApplication implements CommandLineRunner {
    public static final Logger LOG = LoggerFactory.getLogger(RapPluginExampleApplication.class);
    
    @Autowired
    RapPlugin rapPlugin;

	public static void main(String[] args) {
		SpringApplication.run(RapPluginExampleApplication.class, args);
	}

    @Override
    public void run(String... args) throws Exception {
        rapPlugin.registerReadingResourceListener(new ReadingResourceListener() {
            
            @Override
            public List<Observation> readResourceHistory(String resourceId) {
                if("isen1".equals(resourceId))
                    // This is the place to put reading history data of sensor.
                    return new ArrayList<>(Arrays.asList(createObservation(resourceId), createObservation(resourceId), createObservation(resourceId)));
                else 
                    throw new RapPluginException(404, "Sensor not found.");
            }
            
            @Override
            public Observation readResource(String resourceId) {
                if("isen1".equals(resourceId)) {
                    // This is place to put reading data from sensor 
                    return createObservation(resourceId);
                }
                    
                throw new RapPluginException(404, "Sensor not found.");
            }
        });
        
        rapPlugin.registerActuatingResourceListener(new ActuatingResourceListener() {
            @Override
            public void actuateResource(String resourceId, Map<String,Capability> parameters) {
                System.out.println("Called actuation for resource " + resourceId);
                for(Capability capability: parameters.values()) {
                    System.out.println("Capability: " + capability.getName());
                    for(Parameter parameter: capability.getParameters().values()) {
                        System.out.println(" " + parameter.getName() + " = " + parameter.getValue());
                    }
                }
                
                if("iaid1".equals(resourceId)) {
                    // This is place to put actuation code for resource with id
                    System.out.println("iaid1 is actuated");
                    return;
                } else {
                    throw new RapPluginException(404, "Actuating entity not found.");
                }
            }
        });
        
        rapPlugin.registerInvokingServiceListener(new InvokingServiceListener() {
            
            @Override
            public Object invokeService(String resourceId, Map<String, Parameter> parameters) {
                System.out.println("In invoking service of resource " + resourceId);
                for(Parameter p: parameters.values())
                    System.out.println(" Parameter - name: " + p.getName() + " value: " + p.getValue());
                if("isrid1".equals(resourceId)) {
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
        
        ObservationValue obsval = 
                new ObservationValue(
                        "7", 
                        new Property("Temperature", /*"TempIRI",*/ Arrays.asList("Air temperature")), 
                        new UnitOfMeasurement("C", "degree Celsius", /*"C_IRI",*/ null));
        ArrayList<ObservationValue> obsList = new ArrayList<>();
        obsList.add(obsval);
        
        Observation obs = new Observation(sensorId, loc, timestamp, samplet , obsList);
        
        try {
            LOG.debug("Observation: \n{}", new ObjectMapper().writeValueAsString(obs));
        } catch (JsonProcessingException e) {
            LOG.error("Can not convert observation to JSON", e);
        }
        
        return obs;
    }    
}
