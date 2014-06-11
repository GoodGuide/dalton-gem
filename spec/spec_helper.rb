require 'bundler'
require 'jbundler'
Bundler.require

require 'wrong/adapters/rspec'

require 'dalton'

Dir[File.join(File.dirname(__FILE__), 'support/**/*')].each {|f| require(f)}
