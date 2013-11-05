
Here's a list of changes I've made to the [Picasso][1] library


###Cache
Added the possibility to use an external cache instance per request.
Example:

	// creating a cache object with 1MB max size
	Cache cache = new LruCache( 1024*1024*1 );
	
	// now create a new request which will use this cache object
	Picasso.with( this )
		.load( file )
		.withCache( myCache )
		.into( imageView );
	
> ***Remember*** to clear the cache when you don't need that anymore ( using cache.clear() )


###Generators
Generators can be used to load images which cannot be loaded using the common scheme convention. There are different situations when you need to generate a Bitmap which is not directly related to a file or url or even a real bitmap resource.
In this case you can use the scheme "custom.resource" with a Generator.
A Generator is a simple interface with only one method:

	public interface Generator {
		Bitmap decode( String path ) throws IOException;
	}

So you can use a generator in this way:

	Picasso.with(this)
		.load( Uri.parse( "custom.resource://" + file ) )
		.withGenerator( new Generator() {
		
			@Override
			public Bitmap decode( String path ) throws IOException {
				return whatever(path);
			}
		 } )
		.into( imageView );
		

###Resize
Both the original Picasso methods resize and resizeDimen have been modified in the followings new methods:

	public RequestCreator resizeDimen(int targetWidthResId, int targetHeightResId, boolean onlyIfBigger);
	public RequestCreator resize(int targetWidth, int targetHeight, boolean onlyIfBigger);


basically you can pass an option to skip the resize operation if the loaded bitmap is smaller than the passed `targetWidth` and `targetHeight`


###BitmapFactory.Options

Picasso uses a default BitmapFactory.Options object, every time, to decode the required Bitmap.
I've added a method `withOptions` in the RequestCreator which allow you to pass your own Options object which will be used to decode the image.
Example:

	BitmapFactory.Options options = new BitmapFactory.Options();
	options.inPreferredConfig = Config.RGB_565;

	Picasso.with(this)
		.load(file)
		.withOptions(options)
		.into(imageView);


###Fade Time
Added `.fade(ms time)` method in the RequestCreator class in order to let you change the fade in animation time. 
Usage:

	Picasso.with(this)
		.load(file)
		.fade(500)
		.into(imageView);
		



---
Author: [Alessandro Crugnola][2]



[1]: https://github.com/square/picasso
[2]: http//blog.sephiroth.it