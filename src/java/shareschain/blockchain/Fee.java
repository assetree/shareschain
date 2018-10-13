
package shareschain.blockchain;

import shareschain.Constants;

public interface Fee {

    long NEW_ACCOUNT_FEE = Constants.KER_PER_SCTK;

    long getFee(TransactionImpl transaction, Appendix appendage);

    Fee DEFAULT_SCTK_FEE = new Fee.ConstantFee(Constants.KER_PER_SCTK);

    Fee NONE = new Fee.ConstantFee(0L);

    class ConstantFee implements Fee {

        private final long fee;

        public ConstantFee(long fee) {
            this.fee = fee;
        }

        @Override
        public long getFee(TransactionImpl transaction, Appendix appendage) {
            return fee;
        }

    }

}
