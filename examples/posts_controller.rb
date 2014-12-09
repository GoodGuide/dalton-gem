class ApplicationController
  include Dalton::Model::HasDatabase

  def datomic_connection
    App.datomic_connection
  end

  before_filter :refresh_datomic!

  rescue_from Dalton::NotFound do |e|
    render_404 "Could not find #{e.model} with id #{e.id}"
  end

  rescue_from Dalton::ValidationError do |e|
    render_403 "#{e.changes.model} was invalid: #{e.errors.inspect}"
  end

end

class PostsController < ApplicationController
  def index
    author = find(User).entity(params[:author])
    @posts = find(Post).where(author: author)
  end

  def show
    @post = find(Post).entity(params[:id])
  end

  def create
    author = find(User).entity(params[:author])
    @post = Post.create! do |p|
      p.content = params[:content]
      p.author = author.change do |a|
        a.last_post = p
      end
    end
  end

  def update
    find(Post).entity(params[:id]).change! do |p|
      p.content = params[:content]
    end
  end

  def destroy
    find(Post).entity(params[:id]).retract!
  end
end
