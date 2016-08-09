const domready = require('domready')
const client = require('./client')

domready(function () {
  client.initClient()
})
