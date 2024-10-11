function checkHrefUrlXss (url, bank) {
  url = filterXSS(url)
  if (bank) {
    window.open(url)
  } else {
    window.location.href = url
  }
}