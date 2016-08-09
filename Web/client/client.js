/* global easyrtc location prompt*/
const $ = require('jquery')
const _ = require('lodash')
const utils = require('./utils')
const audio = require('./audio')
const viz = require('./charts')
const io = require('socket.io-client')
const feathers = require('feathers-client')

var socket = io('https://rhythm-server.herokuapp.com', {
  'transports': [
    'websocket',
    'flashsocket',
    'htmlfile',
    'xhr-polling',
    'jsonp-polling'
  ]
})

const app = feathers()
.configure(feathers.hooks())
.configure(feathers.socketio(socket))
.configure(feathers.authentication())

var $scope = {
  user: 'sarah',
  roomName: null,
  roomUsers: [],
  app: app
}

function loginSuccess () {
  console.log('login successful')
  $scope.roomUsers.push({participant: $scope.user, meeting: $scope.roomName})
  console.log($scope.roomUsers)
  app.authenticate({
    type: 'local',
    email: 'heroku-email',
    password: 'heroku-password'
  }).then(function (result) {
    console.log('auth result:', result)
    return socket.emit('meetingJoined', {
      participant: $scope.user,
      name: $scope.user,
      participants: $scope.roomUsers,
      meeting: $scope.roomName,
      meetingUrl: location.href,
      consent: true,
      consentDate: new Date().toISOString()
    })
  }).catch(function (err) {
    console.log('ERROR:', err)
  }).then(function (result) {
    console.log('meeting result:', result)
    // audio.startProcessing($scope)
    viz.startMM($scope)
    var turns = app.service('turns')
    turns.on('created', viz.turnsMM)
  })
}

function init () {
  joinRoom()
  $('#leaveRoomLink').click(function () {
    easyrtc.leaveRoom($scope.roomName, function () {
      location.assign(location.href.substring(0, location.href.indexOf('?')))
    })
  })
}

function joinRoom () {
  $scope.roomName = utils.getParam('room')
  if ($scope.roomName === null || $scope.roomName === '' || $scope.roomName === 'null') {
    $scope.roomName = prompt('enter room name:')
    if (location.href.indexOf('?room=') === -1) {
      location.assign(location.href + '?room=' + $scope.roomName)
    } else {
      location.assign(location.href + $scope.roomName)
    }
  } else {
    console.log('entered room: ' + $scope.roomName)
    $('#roomIndicator').html("Currently in room '" + $scope.roomName + "'")
    $('#leaveRoomLink').css('visibility', 'visible')
  }
  loginSuccess()
}

module.exports = {
  initClient: init
}
