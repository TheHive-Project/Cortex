'use strict';

/**
 * @ngdoc overview
 * @name cortex
 * @description
 * # cortex
 *
 * Main module of the application.
 */
angular.module('cortex', [
        'ui.router',
        'ui.bootstrap',
        'ui-notification',
        'angularMoment',
        'angularUtils.directives.dirPagination'
    ])
    .config(function(NotificationProvider) {
        NotificationProvider.setOptions({
            delay: 4000,
            startTop: 20,
            startRight: 10,
            verticalSpacing: 20,
            horizontalSpacing: 20,
            positionX: 'right',
            positionY: 'bottom'
        });
    });
