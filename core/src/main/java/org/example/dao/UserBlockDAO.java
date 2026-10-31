package org.example.dao;

import org.example.UserBlock;

import java.util.List;

public interface UserBlockDAO {
    UserBlock save(UserBlock block);
    List<UserBlock> findByUserId(int userId);
    void deleteById(int id);
}
