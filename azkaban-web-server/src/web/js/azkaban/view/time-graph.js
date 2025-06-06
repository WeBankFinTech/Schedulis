/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

$.namespace('azkaban');

azkaban.TimeGraphView = Backbone.View.extend({
  events: {},

  initialize: function (settings) {
    this.model.bind('render', this.render, this);
    this.model.bind('change:page', this.render, this);
    this.modelField = settings.modelField;
    this.graphContainer = settings.el;
    this.render();
  },

  render: function (self) {
    var series = this.model.get(this.modelField);
    if (series == null) {
      return;
    }

    // Array of points to be passed to Morris.
    var data = [];

    // Map of y value to index for faster look-up in the lineColorsCallback to
    // get the status for each point.
    var indexMap = {};
    for (var i = 0; i < series.length; ++i) {
      if (series[i].startTime == null || series[i].endTime == null) {
        console.log("Each element in series must have startTime and endTime");
        return;
      }
      var startTime = series[i].startTime;
      var endTime = series[i].endTime;
      if (startTime == -1 && endTime == -1) {
        console.log(
            "Ignoring data point with both start and end time invalid.");
        continue;
      }

      var duration = 0;
      if (endTime != -1 && startTime != -1) {
        duration = endTime - startTime;
      }
      if (endTime == -1) {
        endTime = new Date().getTime();
      }
      data.push({
        time: endTime,
        duration: duration,
        // moyenne: series[i].moyenne,
      });

      indexMap[endTime.toString()] = i;
    }

    if (data.length == 0) {
      $(this.graphContainer).hide();
      return;
    } else {
        $(this.graphContainer).show();
    }

    var graphDiv = document.createElement('div');
    $(this.graphContainer).html(graphDiv);

    var lineColorsCallback = function (row, sidx, type) {
      if (type != 'point') {
        if(sidx == 0){
          return "#000000";
        }
        if(sidx == 1){
          return "#c92123";
        }
      }
      if (type == 'point') {
        if(sidx == 1){
          return "#c92123";
        }
      }
      var i = indexMap[row.x.toString()];
      var status = series[i].status;
      if (status == 'SKIPPED') {
        return '#aaa';
      }
      else if (status == 'SUCCEEDED') {
        return '#4e911e';
      }
      else if (status == 'RUNNING') {
        return '#009fc9';
      }
      else if (status == 'PAUSED') {
        return '#c92123';
      }
      else if (status == 'KILLING') {
        return '#ff9999';
      }
      else if (status == 'FAILED' ||
          status == 'FAILED_FINISHING') {
        return '#cc0000';
      }
      else if (status == 'KILLED') {
        return '#660066';
      }
      else {
        return '#ccc';
      }
    };

    var xLabelFormatCallback = function (x) {
      return dateFormat("mm-dd/HH:MM",new Date(x));
    };

    var yLabelFormatCallback = function (y) {
      var seconds = y / 1000.0;
      return seconds.toString() + " s";
    };

    var hoverCallback = function (index, options, content) {
      // Note: series contains the data points in descending order and index
      // is the index into Morris's internal array of data sorted in ascending
      // x order.
      var endTime=new Date(series[options.data.length - index - 1].endTime)
      var timeFormat=getProjectModifyDateFormat(endTime);
      var status = series[options.data.length - index - 1].status;
      var duration=content.substring(content.indexOf('Duration'))
      var contentHtml="<div class='morris-hover-row-label'>"+timeFormat+"</div></div><div class='morris-hover-point' style='color: #000000'>"+duration+"<div class='morris-hover-point'>Status: " + status + "</div>"
      return contentHtml
    };

    Morris.Line({
      element: graphDiv,
      data: data,
      xkey: 'time',
      ykeys: ['duration'],
      labels: ['Duration'],
      // ykeys: ['duration', 'moyenne'],
      // labels: ['Duration', 'Moyenne'],
      lineColors: lineColorsCallback,
      xLabelFormat: xLabelFormatCallback,
      yLabelFormat: yLabelFormatCallback,
      hideHover:'auto',
      hoverCallback: hoverCallback,
      pointSize: [4,0]
    });


  }
});
