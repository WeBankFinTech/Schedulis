/**
 * 设置未来(全局)的AJAX请求默认选项
 * 主要设置了AJAX请求遇到Session过期的情况
 */
$.ajaxSetup({
    beforeSend: function(xhr, setting) {
      xhr.setRequestHeader('csrfToken', localStorage.getItem('csrfToken'));
    },
    complete: function(xhr,status) {
        var sessionStatus = xhr.getResponseHeader('session-status');
        if(sessionStatus == 'timeout') {
            var top = getTopWindow();
            top.location.href = '';
        }else{
          localStorage.setItem('csrfToken', xhr.getResponseHeader("csrfToken"));
        }
    }
});

/**
 * 在页面中任何嵌套层次的窗口中获取顶层窗口
 * @return 当前页面的顶层窗口对象
 */
function getTopWindow(){
    var p = window;
    while(p != p.parent){
        p = p.parent;
    }
    return p;
}