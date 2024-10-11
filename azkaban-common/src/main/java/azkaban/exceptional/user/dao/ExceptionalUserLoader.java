package azkaban.exceptional.user.dao;


import azkaban.exceptional.user.entity.ExceptionalUser;

import java.util.List;

public interface ExceptionalUserLoader {

  void add(ExceptionalUser exceptionalUser) throws Exception;

  void delete(String userId) throws Exception;

  List<ExceptionalUser> fetchAllExceptionUsers(String searchName, int pageNum, int pageSize) throws Exception;

  List<ExceptionalUser> fetchAllExceptionUsers() throws Exception;

  int getTotal(String searchName) throws Exception;
}
