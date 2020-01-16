'use strict';

let path = require('path');
let webpack = require('webpack');
let autoprefixer = require('autoprefixer');
let Manifest = require('manifest-revision-webpack-plugin');
let ExtractTextPlugin = require('extract-text-webpack-plugin');
let HtmlWebpackPlugin = require('html-webpack-plugin');

let rootPublic = path.resolve('./src');
let NODE_ENV = process.env.NODE_ENV || 'production';
let DEVELOPMENT = NODE_ENV === 'production' ? false : true;
let stylesLoader =
  'css-loader?root=' +
  rootPublic +
  '&sourceMap!postcss-loader!sass-loader?outputStyle=expanded&sourceMap=true&sourceMapContents=true';

module.exports = function (_path) {
  let rootAssetPath = _path + 'src';

  let webpackConfig = {
    // entry points
    entry: {
      app: _path + '/src/app/index.bootstrap.js'
    },

    // output system
    output: {
      path: _path + '/dist',
      filename: '[name].js',
      publicPath: '/'
    },

    // resolves modules
    resolve: {
      extensions: ['.js', '.es6', '.jsx', '.scss', '.css'],
      alias: {
        _appRoot: path.join(_path, 'src', 'app'),
        _images: path.join(_path, 'src', 'assets', 'images'),
        _stylesheets: path.join(_path, 'src', 'assets', 'styles'),
        _scripts: path.join(_path, 'src', 'assets', 'js')
      },
      modules: [path.join(_path, 'node_modules')]
    },

    // modules resolvers
    module: {
      rules: [{
          test: /\.html$/,
          use: [{
              loader: 'ngtemplate-loader',
              options: {
                relativeTo: path.join(_path, '/src')
              }
            },
            {
              loader: 'html-loader',
              options: {
                attrs: ['img:src', 'img:data-src']
              }
            }
          ]
        },
        // {
        //   test: /\.js$/,
        //   exclude: [path.resolve(_path, 'node_modules')],
        //   enforce: 'pre',
        //   use: [
        //     {
        //       loader: 'eslint-loader'
        //     }
        //   ]
        // },
        {
          test: /\.js$/,
          exclude: [path.resolve(_path, 'node_modules')],
          use: [{
              loader: 'babel-loader',
              options: {
                cacheDirectory: false
              }
            },
            {
              loader: 'baggage-loader?[file].html&[file].css'
            }
          ]
        },
        {
          test: /\.css$/,
          use: [{
              loader: 'style-loader'
            },
            {
              loader: 'css-loader?sourceMap'
            },
            {
              loader: 'postcss-loader'
            }
          ]
        },
        {
          test: /\.(scss|sass)$/,
          loader: DEVELOPMENT ?
            'style-loader!' + stylesLoader :
            ExtractTextPlugin.extract({
              fallbackLoader: 'style-loader',
              loader: stylesLoader
            })
        },
        {
          test: /\.(woff2|woff|ttf|eot|otf|svg)?(\?v=[0-9]\.[0-9]\.[0-9])?$/,
          use: [{
            loader: 'url-loader',
            options: {
              name: 'assets/fonts/[name]_[hash].[ext]'
            }
          }]
        },
        {
          test: /\.(jpe?g|png|gif)$/i,
          use: [{
            loader: 'url-loader',
            options: {
              name: 'assets/images/[name]_[hash].[ext]',
              limit: 10000
            }
          }]
        }
      ]
    },

    // load plugins
    plugins: [
      new webpack.LoaderOptionsPlugin({
        options: {
          // PostCSS
          postcss: [autoprefixer({
            browsers: ['last 5 versions']
          })],
          debug: true
        }
      }),
      new webpack.ProvidePlugin({
        $: 'jquery',
        jQuery: 'jquery',
        'window.jQuery': 'jquery',
        'window.jquery': 'jquery',

        moment: 'moment',
        'window.moment': 'moment',

        _: 'lodash',
        'window._': 'lodash'
      }),
      new webpack.DefinePlugin({
        NODE_ENV: JSON.stringify(NODE_ENV)
      }),
      new webpack.NoEmitOnErrorsPlugin(),
      new webpack.IgnorePlugin(/^\.\/locale$/, /moment$/),
      new webpack.optimize.AggressiveMergingPlugin({
        moveToParents: true
      }),
      new webpack.optimize.CommonsChunkPlugin({
        name: 'common',
        async: true,
        children: true,
        minChunks: Infinity
      }),
      new Manifest(path.join(_path + '/config', 'manifest.json'), {
        rootAssetPath: rootAssetPath,
        ignorePaths: ['.DS_Store']
      }),
      new ExtractTextPlugin({
        filename: 'assets/styles/css/[name]' +
          (NODE_ENV === 'development' ? '' : '.[chunkhash]') +
          '.css',
        allChunks: true
      }),
      new HtmlWebpackPlugin({
        filename: 'index.html',
        template: path.join(_path, 'src', 'tpl-index.ejs')
      })
    ]
  };

  return webpackConfig;
};