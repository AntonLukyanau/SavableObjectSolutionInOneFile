package org.example;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.example.Solution.*;


public class SolutionTest {

    static Address address;
    static Person person;
    static Business biz;

    @BeforeAll
    static void setup() {
        address = new Address("5455 Apache Trail", "Queen Creek", "AZ", "85243");
        person = new Person("Jane", "Smith", address);
        biz = new Business("Alliance Reservations Network", address);
    }

    @Test
    public void ProgrammerAssessment() {
        assertNull(person.getId());
        person.save();
        assertNotNull(person.getId());

        assertNull(biz.getId());
        biz.save();
        assertNotNull(biz.getId());

        Person savedPerson = Person.find(person.getId());
        assertNotNull(savedPerson);
        assertSame(person.getAddress(), address);
        assertEquals(savedPerson.getAddress(), address);
        assertEquals(person.getId(), savedPerson.getId());
        assertEquals(person.getFirstName(), savedPerson.getFirstName());
        assertEquals(person.getLastName(), savedPerson.getLastName());
        assertNotEquals(person, savedPerson);
        assertNotSame(person, savedPerson);
        assertNotSame(person.getAddress(), savedPerson.getAddress());
        assertEquals(person.getAddress(), savedPerson.getAddress());

        Business savedBusiness = Business.find(biz.getId());
        assertNotNull(savedBusiness);
        assertSame(biz.getAddress(), address);
        assertEquals(savedBusiness.getAddress(), address);
        assertEquals(biz.getId(), savedBusiness.getId());
        assertEquals(biz.getName(), savedBusiness.getName());
        assertNotEquals(biz, savedBusiness);
        assertNotSame(biz, savedBusiness);
        assertNotSame(biz.getAddress(), savedBusiness.getAddress());
        assertEquals(biz.getAddress(), savedBusiness.getAddress());

        var deletedPersonId = person.getId();
        person.delete();
        assertNull(person.getId());
        assertNull(Person.find(deletedPersonId));

        var deletedBizId = biz.getId();
        biz.delete();
        assertNull(biz.getId());
        assertNull(Business.find(deletedBizId));
    }
}
