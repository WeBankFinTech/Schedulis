var isIE9 = document.all && document.addEventListener && !window.atob;
window.watermarkdivs = []; // 承载水印数据
var initConfig = {}; // 初始化的参数
var timer; // 定时器
var Watermark = {
  /*
   * 全局变量
  **/
  WatermarkModel: {
    divX: null, //水印区域X点坐标，相对于文档左端的偏移量
    divY: null, //水印区域Y点坐标，相对于文档顶端的偏移量
    xSpace: 120, //水印与水印之间的左右间隔
    ySpace: 120, //水印与水印之间的上下间隔
    angle: 45, //水印偏转角度
    rows: null, //全部水印行数
    cols: null, //全部水印列数
    content: null, //水印内容
    width: null, //水印宽度
    height: null, //水印高度
    opacity: null, //水印透明度
    fontSize: null, //水印文字大小
    font: null, //水印文字字体
    color: "grey", //水印颜色  "#000000"
    id: '', // 水印区域元素ID
  },

  /*
   * 水印初始化
  **/
  Init: function (elementId, userName, width = 150, height = 70, opacity = 0.2, fontSize = "16px", font = "微软雅黑") {
    initConfig = {
      elementId: elementId,
      userName: userName,
      width: width,
      height: height,
      opacity: opacity,
      fontSize: fontSize,
      font: font
    };

    // 时间相同不进行刷新
    var currentTime = Watermark.GetCurentTime();
    if (Watermark.WatermarkModel.lastTime == currentTime || !userName || userName === "${user_id}") {// username取的${user_id}值，当值为空时传入${user_id}
      return
    }
    Watermark.WatermarkModel.lastTime = currentTime;

    // js 原生写法
    const bodyEle = document.getElementById(initConfig.elementId); //获取水印覆盖区域主元素
    let bodyEleTop = bodyEle.offsetTop - bodyEle.scrollTop; //获取主元素上边框相对于文档顶端的偏移量  offsetTop - scrollTop = offset().top          等号左边是js，右边是jq
    let bodyEleLeft = bodyEle.offsetLeft - bodyEle.scrollLeft; //获取主元素左边框相对于文档左端的偏移量  offsetLeft - scrollLeft = offset().left    等号左边是js，右边是jq
    let bodyEleHeight = bodyEle.clientHeight; //主元素的高
    let bodyEleWidth = bodyEle.clientWidth; //主元素的宽

    // jq 写法
    // const bodyEle = $("#" + initConfig.elementId); //获取水印覆盖区域主元素
    // let bodyEleTop = bodyEle.offset().top; //获取主元素上边框相对于文档顶端的偏移量
    // let bodyEleLeft = bodyEle.offset().left; //获取主元素左边框相对于文档左端的偏移量
    // let bodyEleHeight = bodyEle.height(); //主元素的高
    // let bodyEleWidth = bodyEle.width(); //主元素的宽

    Watermark.WatermarkModel.id = initConfig.elementId; //水印区域元素ID
    Watermark.WatermarkModel.divX = bodyEleLeft; //设置水印区域X点坐标，相对于文档左端的偏移量
    Watermark.WatermarkModel.divY = bodyEleTop; //设置水印区域Y点坐标，相对于文档顶端的偏移量
    // debugger
    Watermark.WatermarkModel.rows = parseInt(Math.ceil(bodyEleHeight / (Watermark.WatermarkModel.height + 20))); //设置全部水印行数
    Watermark.WatermarkModel.cols = parseInt(Math.ceil(bodyEleWidth / (Watermark.WatermarkModel.width + 20))); //设置全部水印列数
    let timeStamp = Watermark.GetCurentTime(); //获取当前时间
    Watermark.WatermarkModel.content = initConfig.userName + " " + timeStamp; //设置水印内容
    Watermark.WatermarkModel.width = initConfig.width; //设置水印宽度
    Watermark.WatermarkModel.height = initConfig.height; //设置水印高度
    Watermark.WatermarkModel.opacity = initConfig.opacity; //设置水印透明度
    Watermark.WatermarkModel.fontSize = initConfig.fontSize; //设置水印文字大小
    Watermark.WatermarkModel.font = initConfig.font; //设置水印文字字体

    Watermark.CreateWatermark();
    Watermark.StartTimer();
    Watermark.InitVisibilityChangeEvent();
    Watermark.ResizeMark(); // 监听window尺寸变化，进行重新创建水印区域
  },

  /*
   * 创建水印
  **/
  CreateWatermark: function () {
    Watermark.RemoveMark();  // 有水印就先清除

    var oTemp = document.createDocumentFragment(); //创建一个虚拟的节点对象
    //创建水印外壳div
    var otdiv = document.getElementById("otdivid");
    // debugger
    if (!otdiv) {
      otdiv = document.createElement('div');
      otdiv.id = "otdivid";
      otdiv.style.pointerEvents = "none";
      document.body.appendChild(otdiv);
      // document.getElementById(initConfig.elementId).appendChild(otdiv);
    }

    let xSpace = Watermark.WatermarkModel.xSpace;
    let ySpace = Watermark.WatermarkModel.ySpace;
    let rows = Watermark.WatermarkModel.rows;
    let cols = Watermark.WatermarkModel.cols;
    let divX = Watermark.WatermarkModel.divX;
    let divY = Watermark.WatermarkModel.divY;
    let content = Watermark.WatermarkModel.content;
    let opacity = Watermark.WatermarkModel.opacity;
    let fontSize = Watermark.WatermarkModel.fontSize;
    let font = Watermark.WatermarkModel.font;
    let width = Watermark.WatermarkModel.width;
    let height = Watermark.WatermarkModel.height;
    let angle = Watermark.WatermarkModel.angle;
    let color = Watermark.WatermarkModel.color;

    let maxWidth = Math.max(document.body.scrollWidth, document.documentElement.scrollWidth) - 20; //最大宽度，减去左右padding
    let maxHeight = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight) - 20; //最大高度，减去上下padding

    //如果全部水印行数列数为0 || （水印偏移量 + 宽高度 * 行列 + 上下左右间隔）> 最大宽高度，则需要对 水印行列数和间隔 重新调整 
    if (cols == 0 || (parseInt(divX + width * cols + xSpace * (cols - 1)) > maxWidth)) {
      cols = parseInt((xSpace + maxWidth - divX) / (width + xSpace));
      xSpace = parseInt(((maxWidth - divX) - width * cols) / (cols - 1)); //重新调整，水印与水印之间的左右间隔
      if (!xSpace) {
        xSpace = 0;
      }
    }
    if (rows == 0 || (parseInt(divY + height * rows + ySpace * (rows - 1)) > maxHeight)) {
      rows = parseInt((ySpace + maxHeight - divY) / (height + ySpace));
      ySpace = parseInt(((maxHeight - divY) - height * rows) / (rows - 1)); //重新调整，水印与水印之间的上下间隔

      if (!ySpace) {
        ySpace = 0;
      }
    }
    let rotation = Watermark.GetRotation(-angle);
    let maskDivX; //每一个水印的X偏移量
    let maskDivY; //每一个水印的Y偏移量
    //双循环创建水印
    for (let i = 0; i < rows; i++) {
      maskDivY = divY + (ySpace + height) * i;
      for (let j = 0; j < cols; j++) {
        maskDivX = divX + (width + xSpace) * j;

        let maskDiv = document.createElement("div"); //水印div
        rotation = Watermark.GetRotation(-angle);
        maskDiv.id = "mask_div" + i + j;

        maskDiv.appendChild(document.createTextNode(content));
        maskDiv.style.webkitTransform = "rotate(-" + angle + "deg)";
        maskDiv.style.MozTransform = "rotate(-" + angle + "deg)";
        maskDiv.style.msTransform = "rotate(-" + angle + "deg)";
        maskDiv.style.OTransform = "rotate(-" + angle + "deg)";
        maskDiv.style.transform = "rotate(-" + angle + "deg)";
        maskDiv.style.visibility = "";
        maskDiv.style.position = "absolute";
        maskDiv.style.pointerEvents = "none"; //在水印覆盖区域内，防止被水印遮挡无法点击
        maskDiv.style.left = maskDivX + "px";
        maskDiv.style.top = maskDivY + "px";
        maskDiv.style.overflow = "hidden";

        maskDiv.style.opacity = opacity;
        if (isIE9) {
          maskDiv.style.filter = "progid:DXImageTransform.Microsoft.Alpha(opacity=" + opacity * 100 + ")";
        } else {
          maskDiv.style.filter = "progid:DXImageTransform.Microsoft.Alpha(opacity=" + opacity * 100 + ") progid:DXImageTransform.Microsoft.Matrix(sizingMethod=\"auto expand\", M11=" + rotation[0] + ", M12=" + rotation[1] + ", M21=" + rotation[2] + ", M22=" + rotation[3] + ")";
        }

        maskDiv.style.fontSize = fontSize;
        maskDiv.style.fontFamily = font;
        maskDiv.style.color = color;
        maskDiv.style.textAlign = "center";
        maskDiv.style.width = width + "px";
        maskDiv.style.height = height + "px";
        maskDiv.style.display = "block";
        maskDiv.style.zIndex = 9999;  // 这里如果不≥ 0，无法预览水印

        //附加到文档碎片中
        otdiv.appendChild(maskDiv);
        oTemp.appendChild(otdiv);
        //控制页面大小变化时水印字体
        window.watermarkdivs.push(otdiv);
      };
    };
    setTimeout(() => {
      document.body.appendChild(oTemp); //水印主体生成完毕后，接在文档后面
    });
  },

  // 移除水印
  RemoveMark: function () {
    if (window.watermarkdivs && window.watermarkdivs.length > 0) {
      window.watermarkdivs = [];
      if (document.getElementById("otdivid")) {
        document.body.removeChild(document.getElementById("otdivid"));
        // document.getElementById(elementId).removeChild(document.getElementById("otdivid"));
        window.watermarkdivs = [];
      }
    }
  },

  // 重新创建水印
  ResizeMark: function () {
    // window.addEventListener("resize", function(params) {
    //     console.log('addEventListener');
    //     Watermark.Init(initConfig.elementId, initConfig.userName, initConfig.width, initConfig.height, initConfig.opacity, initConfig.fontSize, initConfig.font);
    // });
    window.onresize = function () {
      // console.log('onresize');
      if (window.watermarkdivs && window.watermarkdivs.length > 0) { // 水印存在...才重新创建
        if (document.getElementById("otdivid")) {
          // Watermark.Init(initConfig.elementId, initConfig.userName, initConfig.width, initConfig.height, initConfig.opacity, initConfig.fontSize, initConfig.font);
          Watermark.CreateWatermark();
        }
      }
    }
  },

  /*
   * 计算水印偏转参数
  **/
  GetRotation: function (degVal) {
    let deg = degVal * 1;
    let deg2Rad = Math.PI * 2 / 360;
    let rad = deg * deg2Rad;
    let costheta = Math.cos(rad);
    let sintheta = Math.sin(rad);
    let m11 = costheta;
    let m12 = -sintheta;
    let m21 = sintheta;
    let m22 = costheta;
    return [m11, m12, m21, m22];
  },

  /*
   * 获取当前时间
  **/
  GetCurentTime: function () {
    let now = new Date();
    let year = now.getFullYear();       //年
    let month = now.getMonth() + 1;     //月
    let day = now.getDate();            //日
    let hh = now.getHours();            //时
    let mm = now.getMinutes();          //分
    let ss = now.getSeconds();          //秒
    if (month < 10) { month = "0" + month; }
    if (day < 10) { day = "0" + day; }
    if (hh < 10) { hh = "0" + hh; }
    if (mm < 10) { mm = "0" + mm; }
    if (ss < 10) { ss = "0" + ss; }

    var clock = year + "-" + month + "-" + day + " " + hh + ":" + mm; // 精确到分
    return (clock);
  },

  /*
  ** 监听页面是否是激活状态
  */
  InitVisibilityChangeEvent: function () {
    var hiddenProperty = 'hidden' in document ? 'hidden'
      : 'webkitHidden' in document ? 'webkitHidden'
        : 'mozHidden' in document ? 'mozHidden'
          : null;
    var visibilityChangeEvent = hiddenProperty.replace(/hidden/i, 'visibilitychange');

    var onVisibilityChange = function () {
      if (document[hiddenProperty]) {    //页面非激活 
        Watermark.StopTimer()
      } else {   // 页面激活
        Watermark.Init(initConfig.elementId, initConfig.userName, initConfig.width, initConfig.height, initConfig.opacity, initConfig.fontSize, initConfig.font);
        // Watermark.CreateWatermark();
        Watermark.StartTimer();
      }
    }
    document.addEventListener(visibilityChangeEvent, onVisibilityChange);
  },

  /*
  ** 开启定时器
  */
  StartTimer: function () {
    timer = setInterval(function () {  // 每5秒执行一次
      Watermark.Init(initConfig.elementId, initConfig.userName, initConfig.width, initConfig.height, initConfig.opacity, initConfig.fontSize, initConfig.font);
      // Watermark.CreateWatermark();
    }, 5000);
  },

  /*
  ** 关闭定时器
  */
  StopTimer: function () {
    clearInterval(timer);
  }
};

//export { // 很关键——根据用户是要index.html引用（注释掉），还是要import引用（取消注释）
//    Watermark
//}