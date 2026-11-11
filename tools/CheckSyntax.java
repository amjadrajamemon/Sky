import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.util.*;
import java.io.*;
/** Syntax parser does not validate Android SDK symbol resolution. */
public class CheckSyntax {
  public static void main(String[] args)throws Exception{
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> d=new DiagnosticCollector<>();
    try(StandardJavaFileManager files=compiler.getStandardFileManager(d,null,null)){
      Iterable<? extends JavaFileObject> src=files.getJavaFileObjectsFromStrings(Arrays.asList(args));
      JavacTask task=(JavacTask)compiler.getTask(null,files,d,Arrays.asList("-proc:none"),null,src);
      task.parse();
      for(Diagnostic<?> issue:d.getDiagnostics())System.err.println(issue);
      long errors=d.getDiagnostics().stream().filter(x->x.getKind()==Diagnostic.Kind.ERROR).count();
      if(errors>0)System.exit(1);
      System.out.println("Java syntax parse passed for "+args.length+" files (SDK compilation not performed)");
    }
  }
}
