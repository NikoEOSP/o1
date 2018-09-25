package o1.util

import java.io.{FileNotFoundException, FileOutputStream, OutputStreamWriter}

import scala.io.Source
import scala.language.reflectiveCalls


////////////////// NOTE TO STUDENTS //////////////////////////
// For the purposes of our course, it's not necessary
// that you understand or even look at the code in this file.
//////////////////////////////////////////////////////////////


/** The package `o1.util` contains miscellaneous tools that are used internally by some of the 
  * given programs in O1 for added convenience.
  * 
  * '''NOTE TO STUDENTS: In this course, you don't need to understand how this package works 
  * or can be used.'''
  * 
  * This documentation lists only some of the tools in the package. The listed tools are largely 
  * a mix of:
  *  
  *  - functions for simple I/O from files and URLs;
  *  - aliases for easy access (via `import o1.util._`) 
  *    to some of the tools from `scala.util`; and
  *  - implicit classes that add a few convenience methods  
  *    to type in the Scala API. */
private[util] trait iofuncs {

  /** A structural supertype for everything that has a `close` method.
    * @see [[useAndClose]] */
  type Closeable = { def close(): Unit }


  /** Attempts to apply the given `operation` to the given `resource` and returns the result, making 
    * sure to `close` the resource. Calls `close()` on the resource even if the attempt fails with an 
    * exception. Doesn’t catch any exceptions. (This in an implementation of the “loan pattern”.)   
    * @param resource   a resource, such as a file, that has a `close()` method
    * @param operation  an operation that can be applied to `resource` */
  def useAndClose[Resource <: Closeable, Result](resource: Resource)(operation: Resource => Result): Result = {
    // An implementation of the so-called Loan Pattern for using closeable resources such as files.
    try {
      operation(resource)
    } finally {
      resource.close()
    }
  }

  /** Calls `getLines` on the given `Source` and performs the specified `effect` on each of the lines.
    * Calls `close()` on the source even if an exception occurs, but doesn’t catch any exceptions. */
  def forEachLine(source: Source)(effect: String => Unit): Unit = {
    useAndClose(source)( _.getLines.foreach(effect) )
  }


  /** Calls `getLines` on the given `Source` and returns the lines in a vector.
    * Calls `close()` on the source even if an exception occurs, but doesn’t catch any exceptions. 
    * @param source          the source to read the lines from  
    * @param trimEach        whether to remove whitespace around each of the lines
    * @param excludeIfEmpty  whether to exclude (possibly trimmed) empty lines from the return value */
  def readLinesFromSource(source: Source, trimEach: Boolean = true, excludeIfEmpty: Boolean = true): Vector[String] = {
    def processLine(line: String) = {
      val lineContent = if (trimEach) line.trim else line
      if (excludeIfEmpty && lineContent.isEmpty) None else Some(lineContent)
    }
    useAndClose(source)( _.getLines.flatMap(processLine).toVector )
  }

  private def source(url: URL): Source = Source.fromURL(url, "UTF-8")

  
  /** Searches the classpath for a file with the given relative path. Delegates the task to the class loader.
    * Returns the URL of any found file in an `Option` wrapper.  */
  def localURL(resourcePath: String): Option[URL] = Option(this.getClass.getResource("/" + resourcePath))


  /** Searches the classpath for a file with the given relative path and returns a corresponding `Source`.
    * The result comes in an `Option` wrapper in case no such file is accessible. Assumes UTF-8 encoding. */
  def localSource(resourcePath: String): Option[Source] = localURL(resourcePath).map(source)

  

  /** Reads and returns the lines from a classpath-relative text file. The result comes in an 
    * `Option` wrapper in case no such file is accessible. Calls `close()` on the file even if 
    * an exception occurs, but doesn’t catch any exceptions. Assumes UTF-8 encoding.  
    * @param relativePath    the path to a resource; relative to a classpath entry  
    * @param trimEach        whether to remove whitespace around each of the lines
    * @param excludeIfEmpty  whether to exclude (possibly trimmed) empty lines from the return value */
  def readFileLines(relativePath: String, trimEach: Boolean = true, excludeIfEmpty: Boolean = true): Option[Vector[String]] =
    for (source <- localSource(relativePath)) yield readLinesFromSource(source, trimEach, excludeIfEmpty)


  /** Returns, as a single `String`, the entire contents of a classpath-relative text file. The result 
    * comes in an `Option` wrapper in case no such file is accessible. Calls `close()` on the file even 
    * if an exception occurs, but doesn’t catch any exceptions. Assumes UTF-8 encoding.  
    * @param relativePath  the path to a resource; relative to a classpath entry */
  def readTextFile(relativePath: String): Option[String] = for (source <- localSource(relativePath)) yield useAndClose(source)( _.mkString )

  
  
  // LATER: Fix the fact that the functions here now inconsistently operate on classpath-relative and working dir paths.
  
 
  /** Writes the given `text` in a new text file at the given `filePath`. Overwrites any existing file 
    * there. Encodes the output as UTF-8. Calls `close()` on the output stream even if an exception occurs, 
    * but doesn’t catch any exceptions.
    * @param filePath  an absolute path to a local file or a path relative to the working directory 
    */
  def writeTextFile(filePath: String, text: String): Unit = { // absolute or working dir path
    def output = new OutputStreamWriter(new FileOutputStream(filePath), "UTF-8")
    useAndClose(output)( _.write(text) )
  }

  
  /** Searches the classpath for a file with the given relative path and returns a corresponding `File`.
    * The result comes in an `Option` wrapper in case no such file is accessible. */
  def localFile(resourcePath: String): Option[File] = localURL(resourcePath).map(url => new File(url.getPath))

  
  private def tryForResource[Result](pathOrURL: String, transform: URL => Result) = {
    def locally(path: String)            = Try { localURL(path).map(transform).getOrElse(throw new FileNotFoundException) }
    def url(url: String, prefix: String) = Try { transform(new URL(prefix + url)) }
    locally(pathOrURL) orElse url(pathOrURL, "") orElse url(pathOrURL, "http://") orElse url(pathOrURL, "https://")
  }

  
  /** Takes in a path or a URL as a string and tries to locate the corresponding resource.
    * Tries to interpret the given string as one of the following (in order until a match is found):
    *  1. A resource relative to the classpath (as per [[localURL]]).
    *  1. A complete URL string, with protocol and all (e.g., "http://example.com/"). 
    *  1. A URL string with "http://" missing (e.g., "example.com").
    * @param pathOrURL  the possible path or URL to a resource 
    * @return the first `Success`, or a `Failure` if the string isn’t even valid for forming a URL */
  def tryForURL(pathOrURL: String): Try[URL] = tryForResource(pathOrURL, identity)

  
  /** Takes in a path or a URL as a string and tries to construct a corresponding `Source`.
    * Tries to interpret the given string as one of the following, in order:
    *  1. A resource relative to the classpath (as per [[localURL]]).
    *  1. A complete URL string, with protocol and all (e.g., "http://example.com/"). 
    *  1. A URL string with "http://" missing (e.g., "example.com").
    *  1. A URL string with "https://" missing.  
    * Calls `scala.io.Source.fromURL` on each of those candidates until a source is 
    * successfully constructed or all attempts have failed. Assumes UTF-8 encoding.
    * @param pathOrURL  the possible path or URL to a resource 
    * @return the first `Success`, or a `Failure` if none of the candidates can be read as a `Source` */
  def tryForSource(pathOrURL: String): Try[Source] = tryForResource(pathOrURL, source)



  //  def readTextFileUnsafely(path: String)  = this.readTextFile(path).getOrElse(throw new FileNotFoundException(path))
  //  def readFileLinesUnsafely(path: String) = this.readFileLines(path).getOrElse(throw new FileNotFoundException(path))
  
}
