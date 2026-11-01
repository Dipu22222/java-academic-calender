package bd.ac.mbstu.calendar.dao;

import bd.ac.mbstu.calendar.model.User;

import java.util.List;
import java.util.Optional;

/** What we can do with users in the database. */
public interface UserDao {

    /** Saves a new user, sets its id, and returns it. */
    User insert(User user);

    Optional<User> findByUsername(String username);

    List<User> findAll();

    long count();
}
