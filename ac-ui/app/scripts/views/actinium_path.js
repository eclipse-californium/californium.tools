/*global define*/

define([
    'jquery',
    'backbone',
    'bootstrap',
    'views/base',
    'templates'
], function($, Backbone, bootstrap, BaseView, JST) {
    'use strict';

    var ActiniumPathView = BaseView.extend({
        template: JST['app/scripts/templates/actinium_path.ejs'],

        events: {
            'click #connect': 'connect',
            'click .proxy_missing button': 'load_proxy'
        },

        connect: function(e) {
            e.preventDefault();
            var self = this;
            this.$('#connect').button('loading');
            var base_url = this.$('#base_url').val();
            if (base_url[base_url.lenght - 1] != '/') {
                base_url += '/';
            }
            var request = new CoAPRequest();
            request.open('GET', base_url, true);
            request.onload = function() {
                if (request.code == 69) {
                    self.start(base_url);
                } else {
                    self.failed();
                }
            };
            request.onerror = function() {
                self.failed();
            };
            request.send();
        },

        load_proxy: function(e) {
            var self = this;
            if (window.CoAPRequest == undefined) {
                this.$('#connect').prop('disabled', true);
                this.$('#proxy_missing button').button('loading');
                $.getScript("http://localhost:8080/coap.js")
                    .done(function(script, textStatus) {
                        self.$('.proxy_missing').hide();
                        self.$('#connect').prop('disabled', false);
                    }).error(function() {
                        self.$('.proxy_missing').show();
                        self.$('#proxy_missing button').button('reset');
                    });
            } else {
                this.$('#connect').prop('disabled', false);
            }
        },

        start: function(base_url) {
            window.base_url = base_url;
            window.Router.navigate("/home", true);
            window.AppInstanceCollection.fetch({
                reset: true
            });
            window.AppCollection.fetch({
                reset: true
            });
            window.LibCollection.fetch({
                reset: true
            });
        },

        after_render: function() {
            this.load_proxy();
        },

        failed: function() {
            //reset button
            this.$('#connect').button('reset');
        }


    });

    return ActiniumPathView;
});
