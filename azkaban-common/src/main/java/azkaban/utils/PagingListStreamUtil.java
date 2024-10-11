package azkaban.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by zhu on 6/19/18.
 */
public class PagingListStreamUtil<T> {

  /**
   * 总页数
   */
  private int totalPage = 0;

  /**
   * 当前是第几页
   */
  private int curPageNo = 0;

  /**
   * 每页的大小
   */
  private int pageSize = 0;

  /**
   * 每页默认大小
   */
  private static final int DEFAULT_PAGE_SIZE = 500;

  /**
   * 当前页的数据
   */
  private List<T> currentPageData;

  /**
   * 整个集合实体
   */
  private List<T> instanceList = null;

  public PagingListStreamUtil(List<T> pageResult, int pageSize) {
    this.pageSize = pageSize;
    this.instanceList = pageResult;
    init(pageResult, pageSize);
  }


  public PagingListStreamUtil(List<T> pageResult) {
    this(pageResult, DEFAULT_PAGE_SIZE);
  }


  private void init(List<T> pageResult, int pageSize) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("Paging size must be greater than zero.");
    }
    if (null == pageResult) {
      throw new NullPointerException("Paging resource list must be not null.");
    }
    if (pageResult.size() % pageSize > 0) {
      this.totalPage = (pageResult.size() / pageSize) + 1;
    } else {
      this.totalPage = pageResult.size() / pageSize;
    }
  }

  /**
   * 返回当前剩余页数
   *
   * @return
   */
  private int getSurplusPage() {
    if (instanceList.size() % pageSize > 0) {
      return (instanceList.size() / pageSize) + 1;
    } else {
      return instanceList.size() / pageSize;
    }

  }

  /**
   * 返回是否还有下一页数据
   *
   * @return
   */
  public boolean hasNext() {
    return instanceList.size() > 0;
  }

  /**
   * 获取分页后，总的页数
   *
   * @return
   */
  public int getTotalPage() {
    return totalPage;
  }

  public List<T> next() {
    List<T> pagingData = instanceList.stream().limit(pageSize).collect(Collectors.toList());
    instanceList = instanceList.stream().skip(pageSize).collect(Collectors.toList());
    return pagingData;
  }

  /**
   * 返回当前页数
   *
   * @return
   */
  public int getCurPageNo() {
    return totalPage - getSurplusPage();
  }

  public List<T> getCurrentPageData(){
    return this.currentPageData;
  }

  public void setCurrentPageData(List<T> currentPageData){
    this.currentPageData = currentPageData;
  }

  public void setCurPageNo(int curPageNo){
    this.curPageNo = curPageNo < 1 ? 1 : curPageNo > this.totalPage ? this.totalPage : curPageNo;
    setCurrentPageData(currentPageData());
  }

  public List<T> currentPageData(){
    if(this.pageSize==0 || this.totalPage == 1 || this.totalPage == 0){
      return this.instanceList;
    }

    List<T> currentPageData = new ArrayList<>();
    instanceList.stream().skip((this.curPageNo - 1 ) * this.pageSize).limit(this.pageSize).forEach(
        e->currentPageData.add(e)
    );

    return currentPageData;
  }


}
