// Load required modules
const http = require('http')
const express = require('express')
const path = require('path')
const browserify = require('browserify-middleware')
const app = express()

browserify.settings.development('basedir', __dirname)
app.get('/js/main.js', browserify('./client/main.js'))
app.use(express.static(path.join(__dirname, '/public/')))

var webServer = http.createServer(app)

webServer.listen(process.env.PORT, function () {
  // Callback triggered when server is successfully listening. Hurray!
  console.log('Server listening on: http://localhost:%s', process.env.PORT)
})
