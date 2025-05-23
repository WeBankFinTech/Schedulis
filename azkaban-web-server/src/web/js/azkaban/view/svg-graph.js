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

/*
 * SVG graph view.
 */

$.namespace('azkaban');

azkaban.SvgGraphView = Backbone.View.extend({
  events: {},

  initialize: function (settings) {
    this.model.bind('change:selected', this.changeSelected, this);
    this.model.bind('centerNode', this.centerNode, this);
    this.model.bind('change:graph', this.render, this);
    this.model.bind('resetPanZoom', this.resetPanZoom, this);
    this.model.bind('change:update', this.handleStatusUpdate, this);
    this.model.bind('change:disabled', this.handleDisabledChange, this);
    this.model.bind('change:updateAll', this.handleUpdateAllStatus, this);
    this.model.bind('expandFlow', this.expandFlow, this);
    this.model.bind('collapseFlow', this.collapseFlow, this);
    this.model.bind('expandAllFlows', this.expandAllFlows, this);
    this.model.bind('collapseAllFlows', this.collapseAllFlows, this);
    this.model.bind('failedFinishStatusChanges', this.failedFinishStatusChanges, this);
    this.graphMargin = settings.graphMargin ? settings.graphMargin : 25;
    this.svgns = "http://www.w3.org/2000/svg";
    this.xlinksn = "http://www.w3.org/1999/xlink";

    var graphDiv = this.el[0];
    var svg = $(this.el).find('svg')[0];
    if (!svg) {
      svg = this.el;
    }

    this.svgGraph = svg;
    $(this.svgGraph).svg();
    this.svg = $(svg).svg('get');

    $(this.svgGraph).empty();

    // Create mainG node
    var gNode = document.createElementNS(this.svgns, 'g');
    gNode.setAttribute("class", "main graph");
    svg.appendChild(gNode);
    this.mainG = gNode;

    if (settings.rightClick) {
      this.rightClick = settings.rightClick;
    }

    if (settings.dbClick) {
      this.dbClick = settings.dbClick;
    }

    $(svg).svgNavigate();

    var self = this;
    if (self.rightClick && self.rightClick.graph) {
      $(svg).on("contextmenu", function (evt) {
        console.log("graph click");
        var currentTarget = evt.currentTarget;

        self.rightClick.graph(evt, self.model, currentTarget.data);
        return false;
      });
    }

    this.tooltipcontainer = settings.tooltipcontainer ?
      settings.tooltipcontainer : "body";
    if (settings.render) {
      this.render();
    }
  },

  render: function () {
    console.log("graph render");
    $(this.mainG).empty();

    this.graphBounds = this.renderGraph(this.model.get("data"), this.mainG);
    this.resetPanZoom(0);
  },

  renderGraph: function (data, g) {
    g.data = data;
    var nodes = data.nodes
    var edges = data.edges;
    var nodeMap = data.nodeMap;

    // Create a g node for edges, so that they're forced in the back.
    var edgeG = this.svg.group(g);
    if (nodes.length == 0) {
      console.log("No results");
      return;
    };

    // Assign labels
    for (var i = 0; i < nodes.length; ++i) {
      nodes[i].label = ''
      nodes[i].label = nodes[i].id;
    }

    var self = this;

    for (var i = 0; i < nodes.length; ++i) {
      this.drawNode(this, nodes[i], g);
      $(nodes[i].gNode).click(function (evt) {
        var selected = self.model.get("selected");
        if (selected == evt.currentTarget.data) {
          self.model.unset("selected");
        } else {
          self.model.set({ "selected": evt.currentTarget.data });
        }

        evt.stopPropagation();
        evt.cancelBubble = true;
      });
    }

    // layout
    layoutGraph(nodes, edges, 10);
    var bounds = this.calculateBounds(nodes);
    this.moveNodes(nodes);

    for (var i = 0; i < edges.length; ++i) {
      edges[i].toNode = nodeMap[edges[i].to];
      edges[i].fromNode = nodeMap[edges[i].from];
      this.drawEdge(this, edges[i], edgeG);
    }

    this.model.set({ "flowId": data.flowId, "edges": edges });

    var margin = this.graphMargin;
    bounds.minX = bounds.minX ? bounds.minX - margin : -margin;
    bounds.minY = bounds.minY ? bounds.minY - margin : -margin;
    bounds.maxX = bounds.maxX ? bounds.maxX + margin : margin;
    bounds.maxY = bounds.maxY ? bounds.maxY + margin : margin;

    this.assignInitialStatus(this, data);

    if (self.rightClick) {
      if (self.rightClick.node) {
        // Proper children selectors don't work properly on svg
        for (var i = 0; i < nodes.length; ++i) {
          $(nodes[i].gNode).on("contextmenu", function (evt) {
            console.log("node click");
            var currentTarget = evt.currentTarget;
            self.model.set("flowId", currentTarget.data.parent.flowId);
            self.rightClick.node(evt, self.model, currentTarget.data);
            return false;
          });
        }
      }
      if (this.rightClick.graph) {
        $(g).on("contextmenu", function (evt) {
          console.log("graph click");
          var currentTarget = evt.currentTarget;

          self.rightClick.graph(evt, self.model, currentTarget.data);
          return false;
        });
      }
    };
    if (self.dbClick && self.dbClick.nodeDetail) {
      // Proper children selectors don't work properly on svg
      for (var i = 0; i < nodes.length; ++i) {
        $(nodes[i].gNode).on("dblclick", function (evt) {
          console.log("db click");
          var currentTarget = evt.currentTarget;
          self.dbClick.nodeDetail(evt, self.model, currentTarget.data);
          return false;
        });
      }
    }

    $(".node").each(function (d, i) {
      $(this).tooltip({
        container: self.tooltipcontainer,
        html: true,
        delay: {
          show: 500,
          hide: 100
        }
      });
    });

    return bounds;
  },

  handleDisabledChange: function (evt) {
    this.changeDisabled(this.model.get('data'));
  },

  changeDisabled: function (data) {
    for (var i = 0; i < data.nodes.length; ++i) {
      var node = data.nodes[i];
      if (node.disabled) {
        if (node.gNode) {
          addClass(node.gNode, "nodeDisabled");
          var title = "DISABLED (" + node.type + ")";
          title = this.trimFlowProcessTitle(node, title);
          $(node.gNode).attr("title", title).tooltip(
            'fixTitle');
        }
      } else {
        if (node.gNode) {
          removeClass(node.gNode, "nodeDisabled");
          var title = node.status + " (" + node.type +
            ")";
          title = this.trimFlowProcessTitle(node, title);
          $(node.gNode).attr("title", title).tooltip('fixTitle');
        }
        if (node.type == 'flow') {
          this.changeDisabled(node);
        }
      }
    }
  },

  assignInitialStatus: function (evt, data) {
    var switchingFlow = data.switchingFlow
    for (var i = 0; i < data.nodes.length; ++i) {
      var updateNode = data.nodes[i];
      var g = updateNode.gNode;
      var initialStatus = updateNode.status ? updateNode.status : "READY";
      addClass(g, initialStatus);
      var title = initialStatus + " (" + updateNode.type + ")";
      if (updateNode.disabled) {
        addClass(g, "nodeDisabled");
        title = "DISABLED (" + updateNode.type + ")";
      }
      title = this.trimFlowProcessTitle(updateNode, title)
      if (switchingFlow) {
        if (updateNode.expanded) {
          this.collapseAllFlows(updateNode);
        }
        delete data.switchingFlow;
      }
      $(g).attr("title", title);
    }
  },

  changeSelected: function (self) {
    console.log("change selected");
    var selected = this.model.get("selected");
    var previous = this.model.previous("selected");

    if (previous) {
      // Unset previous
      removeClass(previous.gNode, "selected");
    }

    if (selected) {
      this.propagateExpansion(selected);
      var g = selected.gNode;
      addClass(g, "selected");

      console.log(this.model.get("autoPanZoom"));
      if (this.model.get("autoPanZoom")) {
        this.centerNode(selected);
      }
    }
  },

  propagateExpansion: function (node) {
    if (node && node.parent && node.parent.type) {
      this.propagateExpansion(node.parent);
      this.expandFlow(node.parent);
    }
  },

  handleStatusUpdate: function (evt) {
    var updateData = this.model.get("update");
    var data = this.model.get("data");
    this.updateStatusChanges(updateData, data);
  },

  updateStatusChanges: function (updateData, data) {
    // Assumes all changes have been applied.
    if (updateData.nodes) {
      var nodeMap = data.nodeMap;
      if (!nodeMap) {
        return;
      }
      for (var i = 0; i < updateData.nodes.length; ++i) {
        var node = updateData.nodes[i];
        var nodeToUpdate = nodeMap[node.id];

        var g = nodeToUpdate.gNode;
        if (g) {
          this.handleRemoveAllStatus(g);
          addClass(g, nodeToUpdate.status);

          var title = nodeToUpdate.status + " (" + nodeToUpdate.type + ")";
          if (nodeToUpdate.disabled) {
            addClass(g, "nodeDisabled");
            title = "DISABLED (" + nodeToUpdate.type + ")";
          }
          title = this.trimFlowProcessTitle(nodeToUpdate, title);
          $(g).attr("title", title).tooltip('fixTitle');

          if (node.nodes) {
            this.updateStatusChanges(node, nodeToUpdate);
          }
        }
      }
    }
  },
  failedFinishStatusChanges: function () {
    // Assumes all changes have been applied.
    // 失败暂停打开关闭改变节点状态
    var updateData = this.model.get("data");
    if (updateData.nodes) {
      for (var i = 0; i < updateData.nodes.length; ++i) {
        var node = updateData.nodes[i];

        var g = node.gNode;
        if (g) {
          this.handleRemoveAllStatus(g);
          addClass(g, node.status);

          var title = node.status + " (" + node.type + ")";
          if (node.disabled) {
            addClass(g, "nodeDisabled");
            title = "DISABLED (" + node.type + ")";
          }
          title = this.trimFlowProcessTitle(node, title);
          $(g).attr("title", title).tooltip('fixTitle');

          if (node.nodes) {
            this.updateStatusChanges(node, node);
          }
        }
      }
    }
  },
  handleRemoveAllStatus: function (gNode) {
    for (var j = 0; j < statusList.length; ++j) {
      var status = statusList[j];
      removeClass(gNode, status);
    }
  },

  handleRightClick: function (self) {
    if (this.rightClick) {
      var callbacks = this.rightClick;
      var currentTarget = self.currentTarget;
      if (callbacks.node && currentTarget.jobid) {
        callbacks.node(self, this.model, currentTarget.nodeobj);
      } else if (callbacks.edge &&
        (currentTarget.nodeName == "polyline" ||
          currentTarget.nodeName == "line")) {
        callbacks.edge(self, this.model);
      } else if (callbacks.graph) {
        callbacks.graph(self, this.model);
      }
      return false;
    }
    return true;
  },

  drawEdge: function (self, edge, g) {
    var svg = this.svg;
    var svgns = self.svgns;

    var startNode = edge.fromNode;
    var endNode = edge.toNode;

    var startPointY = startNode.y + startNode.height / 2;
    var endPointY = endNode.y - endNode.height / 2;

    if (edge.guides) {
      // Create guide array
      var pointArray = new Array();
      pointArray.push([startNode.x, startPointY]);
      for (var i = 0; i < edge.guides.length; ++i) {
        var edgeGuidePoint = edge.guides[i];
        pointArray.push([edgeGuidePoint.x, edgeGuidePoint.y]);
      }
      pointArray.push([endNode.x, endPointY]);

      edge.line = svg.polyline(g, pointArray, { class: "edge", fill: "none" });
      edge.line.data = edge;
      edge.oldpoints = pointArray;
    } else {
      edge.line = svg.line(g, startNode.x, startPointY, endNode.x, endPointY, { class: "edge" });
      edge.line.data = edge;
    }
  },

  drawNode: function (self, node, g) {
    if (node.type == 'flow') {
      this.drawFlowNode(self, node, g);
    } else if (node.condition != null) {
      this.drawConditionNode(self, node, g);
    } else {
      this.drawBoxNode(self, node, g);
    }
  },

  moveNodes: function (nodes) {
    var svg = this.svg;
    for (var i = 0; i < nodes.length; ++i) {
      var node = nodes[i];
      var gNode = node.gNode;

      svg.change(gNode, { "transform": translateStr(node.x, node.y) });
    }
  },

  expandFlow: function (node) {
    var svg = this.svg;
    var gnode = node.gNode;
    if (!gnode) {
      return;
    }
    node.expanded = true;

    var innerG = gnode.innerG;
    var borderRect = innerG.borderRect;
    var labelG = innerG.labelG;

    var bbox;
    if (!innerG.expandedFlow) {
      var topmargin = 30,
        bottommargin = 5;
      var hmargin = 10;

      var expandedFlow = svg.group(innerG, "", { class: "expandedGraph" });
      this.renderGraph(node, expandedFlow);
      innerG.expandedFlow = expandedFlow;
      removeClass(innerG, "collapsed");
      addClass(innerG, "expanded");
      node.expandedWidth = node.width;
      node.expandedHeight = node.height;
    } else {
      $(innerG.expandedFlow).show();
      removeClass(innerG, "collapsed");
      addClass(innerG, "expanded");
      node.width = node.expandedWidth;
      node.height = node.expandedHeight;
    }

    this.relayoutFlow(node);

    var bounds = this.calculateBounds(this.model.get("data").nodes);

    var margin = this.graphMargin;
    bounds.minX = bounds.minX ? bounds.minX - margin : -margin;
    bounds.minY = bounds.minY ? bounds.minY - margin : -margin;
    bounds.maxX = bounds.maxX ? bounds.maxX + margin : margin;
    bounds.maxY = bounds.maxY ? bounds.maxY + margin : margin;
    this.graphBounds = bounds;
  },

  collapseFlow: function (node) {
    console.log("Collapsing flow");
    var svg = this.svg;
    var gnode = node.gNode;
    node.expanded = false;

    var innerG = gnode.innerG;
    var borderRect = innerG.borderRect;
    var labelG = innerG.labelG;

    removeClass(innerG, "expanded");
    addClass(innerG, "collapsed");

    node.height = node.collapsedHeight;
    node.width = node.collapsedWidth;

    $(innerG.expandedFlow).hide();
    this.relayoutFlow(node);

    var bounds = this.calculateBounds(this.model.get("data").nodes);

    var margin = this.graphMargin;
    bounds.minX = bounds.minX ? bounds.minX - margin : -margin;
    bounds.minY = bounds.minY ? bounds.minY - margin : -margin;
    bounds.maxX = bounds.maxX ? bounds.maxX + margin : margin;
    bounds.maxY = bounds.maxY ? bounds.maxY + margin : margin;
    this.graphBounds = bounds;
  },

  expandAllFlows: function (node) {
    if (node) {
      // expands all embedded flows inside given node
      if (node.type == 'flow') {
        this.expandFlow(node);

        for (var i = 0; i < node.nodes.length; ++i) {
          this.expandAllFlows(node.nodes[i]);
        }
      }
    } else {
      // expands all embedded flows in the graph
      var nodes = this.model.get("data").nodes;
      // Sort here. To keep the node drawing consistent
      nodes.sort(idSort);
      for (var i = 0; i < nodes.length; ++i) {
        this.expandAllFlows(nodes[i]);
      }
    }
  },

  collapseAllFlows: function (node) {
    if (node) {
      // collapse all embedded flows inside given node

      // collapse already rendered nodes of type flow
      if (node.type == 'flow' && node.gNode) {
        this.collapseFlow(node);

        for (var i = 0; i < node.nodes.length; ++i) {
          this.collapseAllFlows(node.nodes[i]);
        }
      }
    } else {
      // collapse all embedded flows in the graph
      var nodes = this.model.get("data").nodes;
      // Sort here. To keep the node drawing consistent
      nodes.sort(idSort);
      for (var i = 0; i < nodes.length; ++i) {
        this.collapseAllFlows(nodes[i]);
      }
    }
  },

  relayoutFlow: function (node) {
    if (node.expanded) {
      this.layoutExpandedFlowNode(node);
    }

    var parent = node.parent;
    if (parent) {
      layoutGraph(parent.nodes, parent.edges, 10);
      this.relayoutFlow(parent);
      // Move all points again.
      this.moveNodeEdges(parent.nodes, parent.edges);
      this.animateExpandedFlowNode(node, 250);
    }
  },

  moveNodeEdges: function (nodes, edges) {
    var svg = this.svg;
    for (var i = 0; i < nodes.length; ++i) {
      var node = nodes[i];
      var gNode = node.gNode;

      $(gNode).animate({ "svgTransform": translateStr(node.x, node.y) }, 250);
    }

    for (var j = 0; j < edges.length; ++j) {
      var edge = edges[j];
      var startNode = edge.fromNode;
      var endNode = edge.toNode;
      var line = edge.line;

      var startPointY = startNode.y + startNode.height / 2;
      var endPointY = endNode.y - endNode.height / 2;

      if (edge.guides) {
        // Create guide array
        var pointArray = new Array();
        pointArray.push([startNode.x, startPointY]);
        for (var i = 0; i < edge.guides.length; ++i) {
          var edgeGuidePoint = edge.guides[i];
          pointArray.push([edgeGuidePoint.x, edgeGuidePoint.y]);
        }
        pointArray.push([endNode.x, endPointY]);

        animatePolylineEdge(svg, edge, pointArray, 250);
        edge.oldpoints = pointArray;
      } else {
        $(line).animate({
          svgX1: startNode.x,
          svgY1: startPointY,
          svgX2: endNode.x,
          svgY2: endPointY
        });
      }
    }
  },

  calculateBounds: function (nodes) {
    var bounds = {};
    var node = nodes[0];
    bounds.minX = node.x - 10;
    bounds.minY = node.y - 10;
    bounds.maxX = node.x + 10;
    bounds.maxY = node.y + 10;

    for (var i = 0; i < nodes.length; ++i) {
      node = nodes[i];
      var centerX = node.width / 2;
      var centerY = node.height / 2;

      var minX = node.x - centerX;
      var minY = node.y - centerY;
      var maxX = node.x + centerX;
      var maxY = node.y + centerY;

      bounds.minX = Math.min(bounds.minX, minX);
      bounds.minY = Math.min(bounds.minY, minY);
      bounds.maxX = Math.max(bounds.maxX, maxX);
      bounds.maxY = Math.max(bounds.maxY, maxY);
    }
    bounds.width = bounds.maxX - bounds.minX;
    bounds.height = bounds.maxY - bounds.minY;

    return bounds;
  },
  trimNodeInfo: function (nodeLabel) {
    var trimFlowName = JSON.parse(sessionStorage.getItem('trimFlowName'));
    if (trimFlowName) {
      nodeLabel = nodeLabel.substring(0, 5)
    }
    return nodeLabel;
  },
  trimFlowProcessTitle: function (node, title) {
    var trimFlowName = JSON.parse(sessionStorage.getItem('trimFlowName'));
    if (trimFlowName) {
      title = node.label + '  ' + title;
    }
    var time = ''
    //　如果开始结束时间不为空，提示框显示
    if (node.startTime && node.startTime > 0) {
      time = '<div>' + getProjectModifyDateFormat(new Date(node.startTime)) + '  ~  '
      if (node.endTime && node.endTime > 0) {
        time += getProjectModifyDateFormat(new Date(node.endTime)) + '</div>'
      } else {
        time += '  --' + '</div>'
      }
      title = time + '<div>' + title + '</div>'
    }
    //是否存在备注
    if (node.comment) {
      title += '<div>Desc：' + node.comment + '</div>'
    }
    title = filterXSS(title)
    return title;
  },
  drawBoxNode: function (self, node, g) {
    var svg = this.svg;
    var horizontalMargin = 8;
    var verticalMargin = 2;

    var nodeG = svg.group(g, "", { class: "node jobnode" });

    var innerG = svg.group(nodeG, "", { class: "nodebox" });
    var borderRect = svg.rect(innerG, 0, 0, 10, 10, 3, 3, { class: "border" });
    var nodeLabel = this.trimNodeInfo(node.label);
    var jobNameText = svg.text(innerG, horizontalMargin, 16, nodeLabel);
    nodeG.innerG = innerG;
    innerG.borderRect = borderRect;

    var labelBBox = jobNameText.getBBox();
    var totalWidth = labelBBox.width + 2 * horizontalMargin;
    var totalHeight = labelBBox.height + 2 * verticalMargin;
    svg.change(borderRect, { width: totalWidth, height: totalHeight });
    svg.change(jobNameText, { y: (totalHeight + labelBBox.height) / 2 - 3 });
    svg.change(innerG, { transform: translateStr(-totalWidth / 2, -totalHeight / 2) });

    node.width = totalWidth;
    node.height = totalHeight;

    node.gNode = nodeG;
    nodeG.data = node;
  },

  drawConditionNode: function (self, node, g) {
    var svg = this.svg;
    var horizontalMargin = 8;
    var verticalMargin = 2;

    var nodeG = svg.group(g, "", { class: "node jobnode" });

    var innerG = svg.group(nodeG, "", { class: "nodebox" });
    var borderRect = svg.rect(innerG, 0, 0, 10, 10, 3, 3, { class: "border" });
    var conditionRect = svg.rect(innerG, 0, 0, 10, 10, 0, 0, { class: "border" });
    var nodeLabel = this.trimNodeInfo(node.label);
    var jobNameText = svg.text(innerG, horizontalMargin, 16, nodeLabel);
    var conditionText = svg.text(innerG, horizontalMargin, 12, "condition", { "font-size": 12 });
    nodeG.innerG = innerG;
    innerG.borderRect = borderRect;

    var labelBBox = jobNameText.getBBox();
    var conditionlabelBBox = conditionText.getBBox();
    var totalWidth = labelBBox.width + 2 * horizontalMargin;
    var totalHeight = 2 * labelBBox.height + 2 * verticalMargin;
    svg.change(borderRect, { width: totalWidth, height: totalHeight });
    svg.change(conditionRect, { width: totalWidth, height: labelBBox.height });
    svg.change(conditionText, { x: (totalWidth - conditionlabelBBox.width) / 2 });
    svg.change(jobNameText, { y: (totalHeight + labelBBox.height) / 2 + 6 });
    svg.change(innerG, { transform: translateStr(-totalWidth / 2, -totalHeight / 2) });

    node.width = totalWidth;
    node.height = totalHeight;

    node.gNode = nodeG;
    nodeG.data = node;
  },

  drawFlowNode: function (self, node, g) {
    var svg = this.svg;

    // Base flow node
    var nodeG = svg.group(g, "", { "class": "node flownode" });

    // Create all the elements
    var innerG = svg.group(nodeG, "", { class: "nodebox collapsed" });
    var borderRect = svg.rect(innerG, 0, 0, 10, 10, 3, 3, { class: "flowborder" });

    // Create label
    var labelG = svg.group(innerG);
    var iconHeight = 20;
    var iconWidth = 21;
    var textOffset = iconWidth + 4;
    var nodeLabel = this.trimNodeInfo(node.label);
    var nodeId = this.trimNodeInfo(node.flowId);
    var jobNameText = svg.text(labelG, textOffset, 1, nodeLabel);
    var flowIdText = svg.text(labelG, textOffset, 11, nodeId, { "font-size": 8 })
    var tempLabelG = labelG.getBBox();
    var iconImage = svg.image(
      labelG, 0, -iconHeight / 2, iconWidth, iconHeight,
      "/images/graph-icon.png", {});

    // Assign key values to make searching quicker
    node.gNode = nodeG;
    nodeG.data = node;

    // Do this because jquery svg selectors don't work
    nodeG.innerG = innerG;
    innerG.borderRect = borderRect;
    innerG.labelG = labelG;

    // Layout everything in the node
    this.layoutFlowNode(self, node);
  },

  layoutFlowNode: function (self, node) {
    var svg = this.svg;
    var horizontalMargin = 8;
    var verticalMargin = 2;

    var gNode = node.gNode;
    var innerG = gNode.innerG;
    var borderRect = innerG.borderRect;
    var labelG = innerG.labelG;

    var labelBBox = labelG.getBBox();
    var totalWidth = labelBBox.width + 2 * horizontalMargin;
    var totalHeight = labelBBox.height + 2 * verticalMargin;

    svg.change(labelG, {
      transform: translateStr(horizontalMargin, labelBBox.height / 2 +
        verticalMargin)
    });
    svg.change(innerG, { transform: translateStr(-totalWidth / 2, -totalHeight / 2) });
    svg.change(borderRect, { width: totalWidth, height: totalHeight });

    node.height = totalHeight;
    node.width = totalWidth;
    node.collapsedHeight = totalHeight;
    node.collapsedWidth = totalWidth;
  },

  layoutExpandedFlowNode: function (node) {
    var svg = this.svg;
    var topmargin = 30,
      bottommargin = 5;
    var hmargin = 10;

    var gNode = node.gNode;
    var innerG = gNode.innerG;
    var borderRect = innerG.borderRect;
    var labelG = innerG.labelG;
    var expandedFlow = innerG.expandedFlow;

    var bound = this.calculateBounds(node.nodes);

    node.height = bound.height + topmargin + bottommargin;
    node.width = bound.width + hmargin * 2;
    svg.change(expandedFlow, {
      transform: translateStr(-bound.minX + hmargin, -bound.minY + topmargin)
    });
    //$(innerG).animate({svgTransform: translateStr(-node.width/2, -node.height/2)}, 50);
    //$(borderRect).animate({svgWidth: node.width, svgHeight: node.height}, 50);
  },

  animateExpandedFlowNode: function (node, time) {
    var gNode = node.gNode;
    var innerG = gNode.innerG;
    var borderRect = innerG.borderRect;

    $(innerG).animate({ svgTransform: translateStr(-node.width / 2, -node.height / 2) }, time);
    $(borderRect).animate({ svgWidth: node.width, svgHeight: node.height }, time);
    $(borderRect).animate({ svgFill: 'white' }, time);
  },

  resetPanZoom: function (duration) {
    var bounds = this.graphBounds;
    var param = {
      x: bounds.minX,
      y: bounds.minY,
      width: (bounds.maxX - bounds.minX),
      height: (bounds.maxY - bounds.minY),
      duration: duration
    };

    this.panZoom(param);
  },

  centerNode: function (node) {
    // The magic of affine transformation.
    // Multiply the inverse root matrix with the current matrix to get the node
    // position.
    // Rather do this than to traverse backwards through the scene graph.
    var ctm = node.gNode.getCTM();
    var globalCTM = this.mainG.getCTM().inverse();
    var otherTransform = globalCTM.multiply(ctm);
    // Also a beauty of affine transformation. The translate is always the
    // left most column of the matrix.
    var x = otherTransform.e - node.width / 2;
    var y = otherTransform.f - node.height / 2;

    this.panZoom({ x: x, y: y, width: node.width, height: node.height });
  },

  globalNodePosition: function (gNode) {
    if (node.parent) {

      var parentPos = this.globalNodePosition(node.parent);
      return { x: parentPos.x + node.x, y: parentPos.y + node.y };
    } else {
      return { x: node.x, y: node.y };
    }
  },

  panZoom: function (params) {
    params.maxScale = 2;
    $(this.svgGraph).svgNavigate("transformToBox", params);
  }
});