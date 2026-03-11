import { Card, CardContent } from "@/components/ui/card";
import { AlertCircle } from "lucide-react";
import { Link } from "wouter";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md mx-4 border-white/10 bg-card/50 backdrop-blur-sm">
        <CardContent className="pt-6">
          <div className="flex mb-4 gap-2">
            <AlertCircle className="h-8 w-8 text-destructive" />
            <h1 className="text-2xl font-bold font-display text-white">Page not found</h1>
          </div>

          <p className="mt-4 text-sm text-muted-foreground">
            The page you requested could not be found. It might have been removed, had its name changed, or is temporarily unavailable.
          </p>

          <div className="mt-6">
            <Link href="/">
              <Button className="w-full bg-primary hover:bg-primary/90 text-white">
                Return to Home
              </Button>
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
