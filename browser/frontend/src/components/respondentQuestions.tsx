export default function RespondentQuestions({ youth, caregiver }: { youth: string[]; caregiver: string[] }) {
   return  ( <section className="flex justify-center gap-6 mt-12 mx-auto">
        <div className="text-md">
          <h2 className="font-bold text-lg text-center">Youth Questions</h2>
          <div className="flex flex-col gap-y-6">
            {youth.map((y, index) => {
              return (
                <div className="shadow-sm hover:shadow-xl text-center w-1/2 hover:shadow-amber-200 rounded-2xl py-3 px-2 mx-auto transition-all duration-200 ease-in-out">{`${index + 1}. ${y}`}</div>
              );
            })}
          </div>
        </div>
        <div className="text-md">
          <h2 className="font-bold text-lg text-center">Caregiver Questions</h2>
          <div className="flex flex-col gap-y-6">
            {caregiver.map((c, index) => {
              return (
                <div className="shadow-sm hover:shadow-xl text-center w-1/2 hover:shadow-amber-200 rounded-2xl py-3 px-2 mx-auto transition-all duration-200 ease-in-out">{`${index + 1}. ${c}`}</div>
              );
            })}
          </div>
        </div>
      </section> )
     }