'use strict';

let _ = require('lodash');
let _configs = {
  // global section
  global: require(__dirname + '/config/webpack/global'),

  // config by enviroments
  production: require(__dirname + '/config/webpack/environments/production'),
  development: require(__dirname + '/config/webpack/environments/development')
};

let _load = function() {
  let ENV = process.env.NODE_ENV ? process.env.NODE_ENV : 'production';

  //console.log('Current Environment: ', ENV);

  // load config file by environment
  return (
    _configs && _.merge(_configs.global(__dirname), _configs[ENV](__dirname))
  );
};

module.exports = _load();
