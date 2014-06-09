require 'bundler'
require 'jbundler'
Bundler.require

require 'dalton'

Dir[File.join(File.dirname(__FILE__), 'support/**/*')].each {|f| require(f)}
