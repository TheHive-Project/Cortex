'use strict';

import $ from 'jquery';
import Dropzone from 'dropzone';

import tpl from './file-chooser.html';

import './file-chooser.scss';

export default function(app) {
  app.directive('fileChooser', fileChooserDirective);

  function fileChooserDirective() {
    'ngInject';

    return {
      restrict: 'A',
      templateUrl: tpl,
      scope: {
        filemodel: '=',
        control: '=',
        preview: '@?',
        accept: '@?'
      },
      link: linkFn
    };

    function linkFn(scope, element) {
      let dropzone;
      element.addClass('dropzone');
      let template = element[0].innerHTML;
      $(element[0].children[0]).remove();
      // create a Dropzone for the element with the given options
      dropzone = new Dropzone(element[0], {
        // 'clickable' : '.dz-clickable',
        url: 'dummy',
        autoProcessQueue: false,
        maxFiles: 1,
        // 'addRemoveLinks' : true,
        createImageThumbnails: angular.isString(scope.preview)
          ? scope.preview === 'true'
          : true,
        acceptedFiles: angular.isString(scope.accept)
          ? scope.accept
          : undefined,
        previewTemplate: template
      });

      dropzone.on('addedfile', function(file) {
        scope.$apply(function() {
          scope.filemodel = file;
        });
      });
      dropzone.on('removedfile', function() {
        setTimeout(function() {
          scope.$apply(function() {
            delete scope.filemodel;
            // scope.filemodel = undefined;
          });
        }, 0);
      });
      dropzone.on('maxfilesexceeded', function(file) {
        this.removeFile(file);
      });
      if (angular.isDefined(scope.control)) {
        scope.control.removeAllFiles = function() {
          dropzone.removeAllFiles();
        };
      }
    }
  }
}
