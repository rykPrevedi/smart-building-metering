package it.unimore.dipi.iot.server.raw.presence;

import it.unimore.dipi.iot.server.raw.SmartObjectResource;

import java.util.UUID;

/**
 * This class represents the prototype of a presence sensor.
 * - people    counts people actually inside the zone.
 * - peopleIn  counts people entry
 * - peopleOut counts people exit
 *
 * @author Riccardo Prevedi
 * @created 11/09/2022 - 17:01
 * @project coap-smart-building
 */

public class PresenceRawSensor extends SmartObjectResource<Integer> {

    private static final String RESOURCE_TYPE = "iot.sensor.presence";

    private Integer peopleIn = 0;

    private Integer peopleOut = 0;

    private Integer people = 0;

    public PresenceRawSensor() {
        super(UUID.randomUUID().toString(), RESOURCE_TYPE);
    }

    @Override
    public Integer loadUpdatedValue() {
        return this.people;
    }

    public void increasePeopleIn() {
        this.peopleIn++;
        totalPeopleCount();
    }

    public void increasePeopleOut() {
        this.peopleOut++;
        totalPeopleCount();
    }

    public void totalPeopleCount() {
        this.people = this.peopleIn - this.peopleOut;
        notifyUpdate(this.people); //People in the zone Listener notification
    }

    public Integer getPeopleIn() {
        return peopleIn;
    }

    public void setPeopleIn(Integer peopleIn) {
        this.peopleIn = peopleIn;
    }

    public Integer getPeopleOut() {
        return peopleOut;
    }

    public void setPeopleOut(Integer peopleOut) {
        this.peopleOut = peopleOut;
    }

    public Integer getPeople() {
        return people;
    }

    public void setPeople(Integer people) {
        this.people = people;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("PresenceRawSensor{");
        sb.append("peopleIn=").append(peopleIn);
        sb.append(", peopleOut=").append(peopleOut);
        sb.append(", people=").append(people);
        sb.append('}');
        return sb.toString();
    }
}
