(function () {
  //嵌入dss　如果 hideHead=true　隐藏头部跟导航栏
  var query = location.search;
  var searchParams = new URLSearchParams(query);
  var hideHead = searchParams.get('hideHead')
  if (hideHead === 'true') {
    sessionStorage.setItem('hideHead', hideHead)
  }
  var getHideHead = sessionStorage.getItem('hideHead');
  if (searchParams.get('hideHead') === 'true' || getHideHead === 'true') {
    $('.navbar-inverse')[0].style.display = 'none'
    $('.az-page-header')[0].style.display = 'none'
    $('.page-breadcrumb')[0].style.display = 'none'
  }
})();