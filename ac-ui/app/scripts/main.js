/*global require*/
'use strict';

require.config({
    shim: {
        bootstrap: {
            deps: ['jquery'],
            exports: 'jquery'
        },
        typeahead: {
            deps: ['jquery'],
            exports: 'jquery'
        }
    },
    paths: {
        jquery: '../bower_components/jquery/dist/jquery',
        backbone: '../bower_components/backbone/backbone',
        underscore: '../bower_components/lodash/lodash',
        bootstrap: '../bower_components/bootstrap-sass-official/assets/javascripts/bootstrap',
        typeahead: '../bower_components/typeahead.js/dist/typeahead.jquery'
    }
});

require([
    'jquery', 'backbone', 'routes/router', 'collections/app_instances', 'collections/apps', 'collections/libs'
], function($, Backbone, Router, AppInstanceCollection, AppCollection, LibCollection) {
    require.s.contexts._.registry['typeahead.js'].factory($);
    window.Router = new Router();
    window.AppInstanceCollection = new AppInstanceCollection();
    window.AppCollection = new AppCollection();
    window.LibCollection = new LibCollection();
    Backbone.history.start();
});
