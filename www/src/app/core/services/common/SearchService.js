'use strict';

import _ from 'lodash';

export default class SearchService {
  constructor($log, $http, $q) {
    'ngInject';

    this.$log = $log;
    this.$http = $http;
    this.$q = $q;

    this.config = {};
  }

  /**
   * @param {*} config
   * {
   *  filter:{},
   *  objectType: '',
   *  range: 'all',
   *  sort: [],
   *  nparent: 1,
   *  nstats: true
   * }
   */
  configure(config) {
    this.config = _.assign({}, config);

    // Set the URL
    if (!_.isString(config.objectType) || config.objectType === 'any') {
      this.config.url = './api/_search';
    } else {
      this.config.url = `./api/${config.objectType.replace(/_/g, '/')}/_search`;
    }

    let queryString = [];

    // Set the range for pagination
    if (_.isString(config.range)) {
      queryString.push('range=' + encodeURIComponent(config.range));
    }

    // Set the sort options
    if (_.isString(config.sort)) {
      queryString.push('sort=' + encodeURIComponent(config.sort));
    } else if (_.isArray(config.sort)) {
      _.forEach(config.sort, s => {
        queryString.push('sort=' + encodeURIComponent(s));
      });
    }

    // Set the parent option
    if (_.isNumber(config.nparent)) {
      queryString.push('nparent=' + config.nparent);
    }

    // Set the stats option
    if (config.nstats === true) {
      queryString.push('nstats=' + config.nstats);
    }

    this.config.params = queryString.join('&');

    return this;
  }

  search() {
    this.$http
      .post(
        this.config.url + (this.config.params ? `?${this.config.params}` : ''),
        {
          query: this.config.filter
        }
      )
      .then(response => {
        response.total = parseInt(response.headers('X-Total'));
        return this.$q.resolve(response);
      })
      .catch(err => this.$q.reject(err));
  }
}
