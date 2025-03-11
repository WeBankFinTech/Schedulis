$(function () {
    var pagesize20 = $(".common-page-size-20")
    var pagesize50 = $(".common-page-size-50")
    var pagesize100 = $(".common-page-size-100")

    var firstPage = $("#common-page-firstPage a")
    var previousPage = $("#common-page-previousPage a")
    var nextPage = $("#common-page-nextPage a")
    var lastPage = $("#common-page-lastPage a")
    var jump = $(".common-page-jump")

    pagesize20.text(wtssI18n.common.pagesize20)
    pagesize50.text(wtssI18n.common.pagesize50)
    pagesize100.text(wtssI18n.common.pagesize100)
    firstPage.text(wtssI18n.common.firstPage)
    previousPage.text(wtssI18n.common.previousPage)
    nextPage.text(wtssI18n.common.nextPage)
    lastPage.text(wtssI18n.common.lastPage)
    jump.text(wtssI18n.common.jump)
});