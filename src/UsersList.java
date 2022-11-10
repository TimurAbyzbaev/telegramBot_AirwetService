import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class UsersList {
    private HashMap<Long, User> userList;

    public UsersList() {
        userList = new HashMap<Long, User>();

    }

    public int size() {
        return userList.size();
    }

    public HashMap<Long, User> getUserList() {
        return userList;
    }

    public void addNewUser(User user) {
        userList.put(user.getUserId(), user);
    }

    public void deleteUser(User user) {
        userList.remove(user.getUserId());
    }
}