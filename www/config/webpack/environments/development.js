'use strict';
let webpack = require('webpack');

module.exports = function (_path) {
  return {
    context: _path,
    devtool: 'source-map',
    devServer: {
      contentBase: './dist',
      hot: true,
      inline: true,
      proxy: {
        '/api': 'http://localhost:9001'
      }
    },
    plugins: [new webpack.HotModuleReplacementPlugin()]
  };
};